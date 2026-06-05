package com.nbn.adfeed.ui.feed;

import android.os.Handler;
import android.os.Looper;

import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.InteractionAction;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.model.SearchRequest;
import com.nbn.adfeed.data.repository.AdRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Feed 层的分页数据目录（UI 层垫片）。
 *
 * <p>通过 {@link AdRepository#loadAds(PageRequest)} 正式分页接口获取数据，
 * 并根据 {@link DataResult.Status} 区分成功、空态、各类错误态回调给 UI 层。</p>
 *
 * <p>网络请求在后台线程执行，结果回调到主线程，避免 NetworkOnMainThreadException。</p>
 *
 * <p>边界说明：本类只依赖 {@link AdRepository} 接口读取数据，不修改人员B的任何文件。</p>
 */
public final class AdCatalog {

    /** 每页条数，与后端 PageRequest.DEFAULT_PAGE_SIZE 对齐。 */
    private static final int PAGE_SIZE = PageRequest.DEFAULT_PAGE_SIZE;

    /** 回调：在主线程返回结果或错误，符合页面直接刷新 UI 的使用习惯。 */
    public interface Callback {
        void onSuccess(FeedPage page);

        void onError(String message);
    }

    private final AdRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // 后台线程池，用于执行可能涉及网络的 repository 调用。
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** 记录上一次请求返回的 nextCursor，用于加载下一页。 */
    private String nextCursor = null;

    /**
     * 刷新打乱缓存：每次下拉刷新时把整个频道的数据拉全、随机打乱后缓存在这里，
     * 加载更多再从这份缓存里按窗口切片。这样在数据层"确定性分页"（每次首屏返回同一批）
     * 的前提下，让用户每次下拉刷新都能看到不同的卡片组合。
     *
     * <p>注意：这是 UI 层的展示性处理，不修改人员B的数据层；真正的"新内容"仍取决于后端。</p>
     */
    private List<AdItem> shuffledPool = null;
    /** shuffledPool 对应的频道，频道切换时缓存失效。 */
    private String shuffledChannel = null;
    /** 拉全频道时最多翻多少页，避免对真实后端发起过多请求。 */
    private static final int MAX_POOL_PAGES = 10;

    /** 注入 Repository（来自人员B），便于后续从 Mock 切换到 Remote。 */
    public AdCatalog(AdRepository repository) {
        this.repository = repository;
    }

    /**
     * 加载指定频道的第 page 页（page 从 0 开始）。
     *
     * @param channel       频道名；传 null 或空表示"精选/全部"。
     * @param page          页码，从 0 开始。
     * @param failOnPurpose 保留参数，兼容旧调用（不再使用）。
     */
    public void loadPage(String channel, int page, boolean failOnPurpose, Callback callback) {
        loadPage(channel, null, null, page, failOnPurpose, callback);
    }

    public void loadPage(String channel,
                         String tagFilter,
                         int page,
                         boolean failOnPurpose,
                         Callback callback) {
        loadPage(channel, tagFilter, null, page, failOnPurpose, callback);
    }

    public void loadPage(String channel,
                         String tagFilter,
                         List<String> searchAdIds,
                         int page,
                         boolean failOnPurpose,
                         Callback callback) {
        // 把单标签包装成列表，复用多标签实现。
        List<String> tagFilters = (tagFilter == null || tagFilter.isEmpty())
                ? null
                : java.util.Collections.singletonList(tagFilter);
        loadPageWithTags(channel, tagFilters, searchAdIds, page, failOnPurpose, callback);
    }

    /**
     * 多标签筛选版本：tagFilters 中的标签做 AND 过滤（广告需同时包含所有标签）。
     *
     * @param tagFilters 筛选标签集合；为空表示不按标签过滤。
     */
    public void loadPageWithTags(String channel,
                                 List<String> tagFilters,
                                 List<String> searchAdIds,
                                 int page,
                                 boolean failOnPurpose,
                                 Callback callback) {
        // 在后台线程执行 repository 调用（可能涉及网络），避免主线程阻塞。
        executor.execute(() -> {
            try {
                boolean hasTags = tagFilters != null && !tagFilters.isEmpty();

                // 如果有搜索 ID 过滤，走搜索过滤路径（再叠加标签 AND 过滤）。
                if (searchAdIds != null && !searchAdIds.isEmpty()) {
                    DataResult<PageResult<AdItem>> result = loadSearchFilteredSync(channel, tagFilters, searchAdIds);
                    postDispatch(result, callback);
                    return;
                }
                // 如果有标签过滤：加载频道数据后本地 AND 过滤（searchAds 只支持单标签，多标签需本地处理）。
                if (hasTags) {
                    DataResult<PageResult<AdItem>> result = loadTagFilteredSync(channel, tagFilters);
                    postDispatch(result, callback);
                    return;
                }

                // 无过滤：刷新（page==0）时拉全频道并打乱缓存，返回首窗口；
                // 加载更多（page>0）从打乱后的缓存里按窗口切片，保证不重复、不遗漏。
                String ch = (channel == null) ? "" : channel;
                if (page == 0) {
                    List<AdItem> pool = loadWholeChannelSync(ch);
                    if (pool.isEmpty()) {
                        // 拉不到数据（远程异常且 mock 也为空）：回退到原始首屏请求，走正常错误/空态分发。
                        DataResult<PageResult<AdItem>> result = repository.loadAds(PageRequest.firstPage(ch, PAGE_SIZE));
                        postDispatch(result, callback);
                        return;
                    }
                    java.util.Collections.shuffle(pool);
                    shuffledPool = pool;
                    shuffledChannel = ch;
                    postDispatch(buildPoolPage(0), callback);
                    return;
                }

                // 加载更多：缓存仍属于当前频道才用，否则重新拉全。
                if (shuffledPool == null || !ch.equals(shuffledChannel)) {
                    List<AdItem> pool = loadWholeChannelSync(ch);
                    java.util.Collections.shuffle(pool);
                    shuffledPool = pool;
                    shuffledChannel = ch;
                }
                postDispatch(buildPoolPage(page), callback);
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("加载失败，点击重试"));
            }
        });
    }

    /**
     * 在后台线程上报互动事件（点赞/收藏/分享/点击/曝光）。
     *
     * <p>repository.updateInteraction() 内部会走 HTTP 请求，必须放到后台线程，
     * 否则在主线程调用会抛 NetworkOnMainThreadException。</p>
     */
    public void updateInteraction(String adId, InteractionAction action) {
        if (adId == null || action == null) {
            return;
        }
        executor.execute(() -> {
            try {
                repository.updateInteraction(adId, action);
            } catch (Exception ignored) {
                // 互动上报失败不影响 UI，UI 已用本地 InteractionStore 即时刷新。
            }
        });
    }

    /**
     * 拉全某个频道的数据：从首屏开始按 nextCursor 翻页，直到没有更多或达到 MAX_POOL_PAGES。
     * 用于刷新时构建"打乱池"。拉不到任何数据时返回空列表。
     */
    private List<AdItem> loadWholeChannelSync(String channel) {
        String ch = (channel == null) ? "" : channel;
        List<AdItem> all = new ArrayList<>();
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        String cursor = null;
        for (int i = 0; i < MAX_POOL_PAGES; i++) {
            PageRequest request = (i == 0)
                    ? PageRequest.firstPage(ch, PAGE_SIZE)
                    : PageRequest.nextPage(ch, cursor, PAGE_SIZE);
            DataResult<PageResult<AdItem>> result = repository.loadAds(request);
            if (result == null || !result.isSuccess() || !result.hasData()) {
                break;
            }
            PageResult<AdItem> pageResult = result.getData();
            for (AdItem item : pageResult.getItems()) {
                // 去重：不同页若返回了相同 id 不重复收集，避免后端 cursor 异常导致死循环式堆积。
                if (item != null && seenIds.add(item.getId())) {
                    all.add(item);
                }
            }
            cursor = pageResult.getNextCursor();
            if (!pageResult.hasMore() || cursor == null) {
                break;
            }
        }
        return all;
    }

    /**
     * 从打乱后的缓存池里切出第 page 页（page 从 0 开始），包装成成功结果。
     * hasMore 表示池里是否还有后续窗口。
     */
    private DataResult<PageResult<AdItem>> buildPoolPage(int page) {
        List<AdItem> pool = (shuffledPool == null) ? new ArrayList<>() : shuffledPool;
        int start = page * PAGE_SIZE;
        if (start >= pool.size()) {
            return DataResult.success(
                    new PageResult<>(new ArrayList<>(), "page_" + (page + 1), null, false,
                            page + 1, PAGE_SIZE, pool.size(), "shuffled"),
                    "shuffled");
        }
        int end = Math.min(start + PAGE_SIZE, pool.size());
        boolean hasMore = end < pool.size();
        String nextCursor = hasMore ? "page_" + (page + 2) : null;
        List<AdItem> window = new ArrayList<>(pool.subList(start, end));
        return DataResult.success(
                new PageResult<>(window, "page_" + (page + 1), nextCursor, hasMore,
                        page + 1, PAGE_SIZE, pool.size(), "shuffled"),
                "shuffled");
    }

    /** 按标签 AND 过滤：加载频道首屏数据后，在本地保留同时包含所有标签的广告。 */
    private DataResult<PageResult<AdItem>> loadTagFilteredSync(String channel, List<String> tagFilters) {
        String ch = (channel == null) ? "" : channel;
        PageRequest request = PageRequest.firstPage(ch, PAGE_SIZE);
        DataResult<PageResult<AdItem>> result = repository.loadAds(request);

        if (result == null || !result.isSuccess() || !result.hasData()) {
            return result;
        }

        List<AdItem> filtered = TagFilter.byTags(result.getData().getItems(), tagFilters);
        return DataResult.success(
                new PageResult<>(filtered, "page_1", null, false, 1, PAGE_SIZE, filtered.size(), "tag_filtered"),
                "tag_filtered"
        );
    }

    /** 按搜索 ID 过滤：先加载频道数据，再在本地过滤匹配的广告 ID（可叠加标签 AND 过滤）。 */
    private DataResult<PageResult<AdItem>> loadSearchFilteredSync(String channel, List<String> tagFilters,
                                                                   List<String> searchAdIds) {
        String ch = (channel == null) ? "" : channel;
        PageRequest request = PageRequest.firstPage(ch, PAGE_SIZE);
        DataResult<PageResult<AdItem>> result = repository.loadAds(request);

        if (result == null || !result.isSuccess() || !result.hasData()) {
            return result;
        }

        // 从结果中过滤出匹配的广告 ID。
        List<AdItem> allItems = result.getData().getItems();
        List<AdItem> filtered = new ArrayList<>();
        for (AdItem item : allItems) {
            if (searchAdIds.contains(item.getId())) {
                filtered.add(item);
            }
        }
        // 再按标签 AND 二次过滤（如果有）。
        if (tagFilters != null && !tagFilters.isEmpty()) {
            filtered = TagFilter.byTags(filtered, tagFilters);
        }

        return DataResult.success(
                new PageResult<>(filtered, "page_1", null, false, 1, PAGE_SIZE, filtered.size(), "filtered"),
                "filtered"
        );
    }

    /**
     * 在主线程根据 {@link DataResult.Status} 分发回调：
     * SUCCESS / FALLBACK → onSuccess
     * EMPTY → onSuccess(空列表)
     * TIMEOUT / PARSE_ERROR / REMOTE_ERROR → onError(对应提示)
     */
    private void postDispatch(DataResult<PageResult<AdItem>> result, Callback callback) {
        mainHandler.post(() -> dispatchResult(result, callback));
    }

    private void dispatchResult(DataResult<PageResult<AdItem>> result, Callback callback) {
        if (result == null) {
            callback.onError("数据加载异常");
            return;
        }

        switch (result.getStatus()) {
            case SUCCESS:
            case FALLBACK:
                PageResult<AdItem> pageResult = result.getData();
                if (pageResult == null || pageResult.isEmpty()) {
                    callback.onSuccess(new FeedPage(new ArrayList<>(), false));
                } else {
                    // 记录 nextCursor 供下一页使用。
                    nextCursor = pageResult.getNextCursor();
                    callback.onSuccess(new FeedPage(
                            new ArrayList<>(pageResult.getItems()),
                            pageResult.hasMore()));
                }
                break;
            case EMPTY:
                callback.onSuccess(new FeedPage(new ArrayList<>(), false));
                break;
            case TIMEOUT:
                callback.onError("网络超时，请检查网络后重试");
                break;
            case PARSE_ERROR:
                callback.onError("数据解析失败，请稍后重试");
                break;
            case REMOTE_ERROR:
                callback.onError("服务异常，请稍后重试");
                break;
            default:
                callback.onError("加载失败，点击重试");
                break;
        }
    }
}
