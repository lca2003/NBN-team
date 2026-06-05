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
        // 在后台线程执行 repository 调用（可能涉及网络），避免主线程阻塞。
        executor.execute(() -> {
            try {
                // 如果有搜索 ID 过滤，走 searchAds 路径。
                if (searchAdIds != null && !searchAdIds.isEmpty()) {
                    DataResult<PageResult<AdItem>> result = loadSearchFilteredSync(channel, tagFilter, searchAdIds);
                    postResult(result, tagFilter, searchAdIds, callback);
                    return;
                }
                // 如果有 tag 过滤，走 searchAds(tag) 路径。
                if (tagFilter != null && !tagFilter.isEmpty()) {
                    SearchRequest searchRequest = SearchRequest.tag(tagFilter);
                    DataResult<PageResult<AdItem>> result = repository.searchAds(searchRequest);
                    postDispatch(result, callback);
                    return;
                }

                // 正式分页：第一页用 firstPage，后续用 nextPage。
                String ch = (channel == null) ? "" : channel;
                PageRequest request;
                if (page == 0) {
                    request = PageRequest.firstPage(ch, PAGE_SIZE);
                    nextCursor = null; // 重置 cursor。
                } else {
                    // 如果没有 nextCursor，构造一个 page_N 格式的 cursor。
                    String cursor = (nextCursor != null) ? nextCursor : "page_" + (page + 1);
                    request = PageRequest.nextPage(ch, cursor, PAGE_SIZE);
                }

                DataResult<PageResult<AdItem>> result = repository.loadAds(request);
                postDispatch(result, callback);
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

    /** 按搜索 ID 过滤：先加载频道数据，再在本地过滤匹配的广告 ID。 */
    private DataResult<PageResult<AdItem>> loadSearchFilteredSync(String channel, String tagFilter,
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
        // 再按 tag 二次过滤（如果有）。
        if (tagFilter != null && !tagFilter.isEmpty()) {
            filtered = TagFilter.byTag(filtered, tagFilter);
        }

        return DataResult.success(
                new PageResult<>(filtered, "page_1", null, false, 1, PAGE_SIZE, filtered.size(), "filtered"),
                "filtered"
        );
    }

    /** 在主线程分发搜索过滤结果。 */
    private void postResult(DataResult<PageResult<AdItem>> result,
                            String tagFilter, List<String> searchAdIds,
                            Callback callback) {
        postDispatch(result, callback);
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
