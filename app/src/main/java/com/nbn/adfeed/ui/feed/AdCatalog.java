package com.nbn.adfeed.ui.feed;

import android.os.Handler;
import android.os.Looper;

import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.InteractionAction;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.repository.AdRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Feed 层的分页数据适配器（UI 层垫片），每次只加载一页，不再预拉全频道。
 *
 * <p>首页用 {@link PageRequest#firstPage}，加载更多用 {@link PageRequest#nextPage}。
 * 标签筛选和搜索过滤在加载后本地 AND 处理。</p>
 */
public final class AdCatalog {

    private static final int PAGE_SIZE = PageRequest.DEFAULT_PAGE_SIZE;
    // 首页加载更大窗口，覆盖频道全部候选（30条），给 shuffle 留足素材
    private static final int FIRST_PAGE_SIZE = 30;

    public interface Callback {
        void onSuccess(FeedPage page);
        void onError(String message);
    }

    private final AdRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // 数据加载用多线程池：快速连点标签/切频道时并发请求，不会排队阻塞。
    private final ExecutorService dataExecutor = Executors.newCachedThreadPool();
    // 互动上报用独立单线程池，永远不会被数据加载拥塞。
    private final ExecutorService interactionExecutor = Executors.newSingleThreadExecutor();

    /** 请求版本号：每次新请求 +1，回调时版本不匹配则丢弃陈旧结果。 */
    private final AtomicInteger requestVersion = new AtomicInteger(0);

    /** 打乱缓存：首页拉全频道后 shuffle，后续 loadMore 从池里切片。频道/标签/搜索变化时失效。 */
    private List<AdItem> shuffledPool = null;
    private String poolChannel = "";
    private List<String> poolTags = null;
    private List<String> poolSearchIds = null;


    public AdCatalog(AdRepository repository) {
        this.repository = repository;
    }

    public void loadPage(String channel, int page, boolean failOnPurpose, Callback callback) {
        loadPageWithTags(channel, null, null, page, failOnPurpose, callback);
    }

    public void loadPage(String channel, String tagFilter, int page,
                         boolean failOnPurpose, Callback callback) {
        List<String> filters = (tagFilter == null || tagFilter.isEmpty())
                ? null : java.util.Collections.singletonList(tagFilter);
        loadPageWithTags(channel, filters, null, page, failOnPurpose, callback);
    }

    public void loadPage(String channel, String tagFilter, List<String> searchAdIds,
                         int page, boolean failOnPurpose, Callback callback) {
        List<String> filters = (tagFilter == null || tagFilter.isEmpty())
                ? null : java.util.Collections.singletonList(tagFilter);
        loadPageWithTags(channel, filters, searchAdIds, page, failOnPurpose, callback);
    }

    /**
     * 单页加载：无筛选时首页一次拉全(30条)打乱入池，后续 loadMore 从池切片(10条/页)；
     * 有标签/搜索筛选时直接请求，不做池化。
     */
    public void loadPageWithTags(String channel, List<String> tagFilters,
                                 List<String> searchAdIds, int page,
                                 boolean failOnPurpose, Callback callback) {
        String ch = (channel == null) ? "" : channel;
        boolean hasFilter = (tagFilters != null && !tagFilters.isEmpty())
                || (searchAdIds != null && !searchAdIds.isEmpty());

        // 筛选/搜索时不走池，直接单页加载
        if (hasFilter) {
            directLoad(ch, tagFilters, searchAdIds, page, callback);
            return;
        }

        int idx = requestVersion.incrementAndGet();

        // page==0 或池失效：重新拉全频道
        if (page == 0 || shuffledPool == null
                || !ch.equals(poolChannel)
                || poolTags != null || poolSearchIds != null) {
            dataExecutor.execute(() -> {
                try {
                    DataResult<PageResult<AdItem>> result = repository.loadAds(
                            PageRequest.firstPage(ch, FIRST_PAGE_SIZE));
                    if (idx != requestVersion.get()) return;
                    if (result == null || (!result.isSuccess() && !result.isFallback())) {
                        postDispatch(result, callback);
                        return;
                    }
                    PageResult<AdItem> pageResult = result.getData();
                    if (pageResult == null) { postDispatch(result, callback); return; }
                    List<AdItem> all = new ArrayList<>(pageResult.getItems());
                    java.util.Collections.shuffle(all);
                    shuffledPool = all;
                    poolChannel = ch;
                    poolTags = null;
                    poolSearchIds = null;
                    postPoolPage(0, result.getSource(), callback);
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("加载失败，点击重试"));
                }
            });
            return;
        }

        // page>0 且池有效：从池切片
        postPoolPage(page, "pool", callback);
    }

    /** 直接加载（筛选/搜索场景，不走池）。 */
    private void directLoad(String ch, List<String> tagFilters, List<String> searchAdIds,
                            int page, Callback callback) {
        int idx = requestVersion.incrementAndGet();
        final PageRequest request = (page == 0)
                ? PageRequest.firstPage(ch, PAGE_SIZE)
                : PageRequest.nextPage(ch, null, PAGE_SIZE);
        dataExecutor.execute(() -> {
            try {
                DataResult<PageResult<AdItem>> result = repository.loadAds(request);
                if (idx != requestVersion.get()) return;
                if (result == null || (!result.isSuccess() && !result.isFallback())) {
                    postDispatch(result, callback);
                    return;
                }
                PageResult<AdItem> pr = result.getData();
                if (pr == null) { postDispatch(result, callback); return; }
                List<AdItem> items = new ArrayList<>(pr.getItems());
                if (searchAdIds != null && !searchAdIds.isEmpty()) items = TagFilter.byAdIds(items, searchAdIds);
                if (tagFilters != null && !tagFilters.isEmpty()) items = TagFilter.byTags(items, tagFilters);
                boolean hasMore = pr.hasMore() && pr.getNextCursor() != null && !pr.getNextCursor().isEmpty();
                PageResult<AdItem> filtered = new PageResult<>(items, pr.getCurrentCursor(),
                        pr.getNextCursor(), hasMore, pr.getPageNumber(), PAGE_SIZE, items.size(), result.getSource());
                DataResult<PageResult<AdItem>> out = result.isFallback()
                        ? DataResult.fallback(filtered, result.getSource(), result.getMessage(), null)
                        : DataResult.success(filtered, result.getSource());
                postDispatch(out, callback);
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("加载失败，点击重试"));
            }
        });
    }

    /** 从 shuffledPool 切第 page 页（10条/页）。池见底时重新打乱再循环，永不显示"没有更多"。 */
    private void postPoolPage(int page, String source, Callback callback) {
        mainHandler.postDelayed(() -> {
            // 人为 1s 加载转圈，让底部 footer 可见
            List<AdItem> pool = shuffledPool == null ? new ArrayList<>() : shuffledPool;
            if (pool.isEmpty()) {
                callback.onSuccess(new FeedPage(new ArrayList<>(), true));
                return;
            }
            int start = (page * PAGE_SIZE) % pool.size();
            int end = Math.min(start + PAGE_SIZE, pool.size());
            // 当前窗口不够 10 条则包绕（循环队列）
            int remaining = PAGE_SIZE - (end - start);
            List<AdItem> pageItems = new ArrayList<>(pool.subList(start, end));
            if (remaining > 0 && end >= pool.size()) {
                // 后面不够了，从池头补满 + 重新打乱，让下一轮顺序不同
                pageItems.addAll(pool.subList(0, Math.min(remaining, end)));
                java.util.Collections.shuffle(pool);
            }
            // 永远 hasMore=true
            callback.onSuccess(new FeedPage(pageItems, true));
        }, 1000L);
    }

    public void updateInteraction(String adId, InteractionAction action) {
        if (adId == null || action == null) return;
        interactionExecutor.execute(() -> {
            try {
                repository.updateInteraction(adId, action);
            } catch (Exception ignored) { }
        });
    }

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
