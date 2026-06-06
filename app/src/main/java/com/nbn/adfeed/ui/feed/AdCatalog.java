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

    /** 上一页返回的 nextCursor，loadMore 时用。频道切换时重置。 */
    private String lastNextCursor = null;

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
     * 单页加载。page==0 用 firstPage，page>0 用 nextPage + lastNextCursor。
     * 标签筛选和搜索 ID 过滤在拿到数据后本地处理。
     */
    public void loadPageWithTags(String channel, List<String> tagFilters,
                                 List<String> searchAdIds, int page,
                                 boolean failOnPurpose, Callback callback) {
        String ch = (channel == null) ? "" : channel;
        final PageRequest request;
        if (page == 0) {
            lastNextCursor = null;
            request = PageRequest.firstPage(ch, PAGE_SIZE);
        } else {
            request = PageRequest.nextPage(ch, lastNextCursor, PAGE_SIZE);
        }

        // 递增版本号：快速连点时只有最后一条请求的结果被消费
        final int version = requestVersion.incrementAndGet();

        dataExecutor.execute(() -> {
            try {
                DataResult<PageResult<AdItem>> result = repository.loadAds(request);
                if (version != requestVersion.get()) return; // 陈旧请求，丢弃
                if (result == null || (!result.isSuccess() && !result.isFallback())) {
                    postDispatch(result, callback);
                    return;
                }

                PageResult<AdItem> pageResult = result.getData();
                if (pageResult == null) {
                    postDispatch(result, callback);
                    return;
                }

                // 本地过滤：标签 AND + 搜索 ID
                List<AdItem> items = new ArrayList<>(pageResult.getItems());
                if (searchAdIds != null && !searchAdIds.isEmpty()) {
                    items = TagFilter.byAdIds(items, searchAdIds);
                }
                if (tagFilters != null && !tagFilters.isEmpty()) {
                    items = TagFilter.byTags(items, tagFilters);
                }

                lastNextCursor = pageResult.getNextCursor();
                boolean hasMore = pageResult.hasMore() && lastNextCursor != null;
                PageResult<AdItem> filtered = new PageResult<>(
                        items, pageResult.getCurrentCursor(), lastNextCursor, hasMore,
                        pageResult.getPageNumber(), PAGE_SIZE, items.size(),
                        result.getSource());

                DataResult<PageResult<AdItem>> out = result.isFallback()
                        ? DataResult.fallback(filtered, result.getSource(), result.getMessage(), null)
                        : DataResult.success(filtered, result.getSource());
                postDispatch(out, callback);
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("加载失败，点击重试"));
            }
        });
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
