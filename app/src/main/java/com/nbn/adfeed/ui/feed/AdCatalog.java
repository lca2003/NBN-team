package com.nbn.adfeed.ui.feed;

import android.os.Handler;
import android.os.Looper;

import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.repository.AdRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Feed 层的分页数据目录（UI 层垫片）。
 *
 * <p>背景：人员B当前的 {@link AdRepository} 只提供按频道/关键词读取的“种子数据”，还没有真正的
 * 分页接口。为了让人员A的“上拉加载更多 / 下拉刷新 / 加载中-空态-错误态”能完整跑通，这里在 UI 层
 * 把种子数据循环复制成多页，并模拟网络延迟和偶发失败。</p>
 *
 * <p>边界说明：本类只依赖 {@link AdRepository} 接口读取数据，不修改人员B的任何文件。等人员B
 * 提供真实分页接口后，把 {@link #loadPage} 内部替换成真实请求即可，Feed/UI 层无需改动。</p>
 */
public final class AdCatalog {

    /** 每页条数。 */
    private static final int PAGE_SIZE = 6;
    /** 模拟分页总页数，便于演示“加载更多直到没有更多”。 */
    private static final int MAX_PAGE = 4;
    /** 模拟网络耗时，用于展示“加载中”态。 */
    private static final long FAKE_LATENCY_MS = 650L;

    /** 回调：在主线程返回结果或错误，符合页面直接刷新 UI 的使用习惯。 */
    public interface Callback {
        void onSuccess(FeedPage page);

        void onError(String message);
    }

    private final AdRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 注入 Repository（来自人员B），便于后续从 Mock 切换到 Remote。 */
    public AdCatalog(AdRepository repository) {
        this.repository = repository;
    }

    /**
     * 加载指定频道的第 page 页（page 从 0 开始）。
     *
     * @param channel       频道名；传 null 或空表示“精选/全部”，使用初始流。
     * @param page          页码，从 0 开始。
     * @param failOnPurpose 是否故意失败，用于演示“错误态 + 重试”。
     */
    public void loadPage(String channel, int page, boolean failOnPurpose, Callback callback) {
        loadPage(channel, null, page, failOnPurpose, callback);
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
        // 用主线程 Handler 延迟回调，模拟异步网络请求，让 UI 能展示 loading 态。
        mainHandler.postDelayed(() -> {
            if (failOnPurpose) {
                callback.onError("网络开小差了，点击重试");
                return;
            }

            // 通过 B 的接口拿种子数据：有频道按频道取，否则取初始流。
            List<AdItem> seed = (channel == null || channel.isEmpty())
                    ? repository.getInitialAds()
                    : repository.getAdsByChannel(channel);
            //聊天搜索过滤，用于显示搜索结果
            seed = TagFilter.byAdIds(seed, searchAdIds);
            //按tag过滤 TODO 考虑后端网络请求时修改前面几行代码
            seed = TagFilter.byTag(seed, tagFilter);

            // 频道下可能没有任何广告 -> 直接返回空页，触发“空态”。
            if (seed.isEmpty()) {
                callback.onSuccess(new FeedPage(new ArrayList<>(), false));
                return;
            }

            // 把种子数据循环铺成一页，模拟分页的“源源不断”。
            List<AdItem> pageItems = buildPageItems(seed, page);
            boolean hasMore = page + 1 < MAX_PAGE;
            callback.onSuccess(new FeedPage(pageItems, hasMore));
        }, FAKE_LATENCY_MS);
    }

    /**
     * 用种子数据循环填充出第 page 页。
     *
     * <p>注意：复制 {@link AdItem} 的字段构造新对象会更“干净”，但 AdItem 的互动状态需要全局共享，
     * 因此这里直接复用种子对象引用；互动状态统一交由 {@link InteractionStore} 按 adId 管理，
     * 同一条广告在不同页/详情页之间共享同一份点赞收藏状态。</p>
     */
    private List<AdItem> buildPageItems(List<AdItem> seed, int page) {
        List<AdItem> result = new ArrayList<>(PAGE_SIZE);
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = (page * PAGE_SIZE + i) % seed.size();
            result.add(seed.get(index));
        }
        return result;
    }
}
