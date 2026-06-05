package com.nbn.adfeed.ui.feed;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.nbn.adfeed.R;
import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.analytics.event.AdAnalyticsEventCounts;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.repository.AdRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数据加载控制器，从 FeedFragment 中提取。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>频道切换与频道参数映射；</li>
 *   <li>下拉刷新（首屏替换）与上拉加载更多（追加）；</li>
 *   <li>分页状态管理（当前页、是否有更多、加载中标记）；</li>
 *   <li>首屏状态切换：加载中 / 空态 / 错误态；</li>
 *   <li>从 SQLite 同步曝光/点击计数到 InteractionStore。</li>
 * </ul>
 *
 * <p>加载完成后自动触发曝光检测，并通过回调通知 Fragment 更新频道 Tab 选中态。</p>
 */
final class FeedDataController {

    /** 频道列表，对应课题的"精选 / 电商 / 本地"。 */
    static final List<String> CHANNELS = Arrays.asList("精选", "电商", "本地");
    /** "精选"视作全部初始流，传空字符串给 AdCatalog。 */
    private static final String CHANNEL_FEATURED = "精选";

    // ---- 分页与频道状态 ----
    private String currentChannel = CHANNEL_FEATURED;
    private int currentPage = 0;
    private boolean hasMore = true;
    private boolean isLoading = false;
    /** 演示用：让下一次加载更多故意失败一次，展示错误态与重试。 */
    @SuppressWarnings("unused")
    private boolean shouldFailNextLoadMore = false;

    /** Fragment 是否已 attached，用于回调中安全判断。 */
    private boolean attached = false;

    // ---- 后台线程池（SQLite 读移出主线程） ----
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ---- 外部依赖 ----
    private AdRepository repository;
    private AdCatalog adCatalog;
    private InteractionStore interactionStore;
    private AnalyticsTracker analyticsTracker;
    private FeedAdapter adapter;
    private FeedFilterManager filterManager;
    private FeedExposureDelegate exposureDelegate;

    // ---- 视图引用（仅用于状态切换） ----
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar loadingView;
    private View statusContainer;
    private TextView statusView;
    private View statusRetryHint;

    /** 频道切换时回调，让 Fragment 更新 Tab 按钮选中态。 */
    interface OnChannelChangedListener {
        void onChannelChanged(String channel);
    }

    /**
     * 绑定所有外部依赖。在 Fragment.onViewCreated 中调用。
     */
    void bind(AdRepository repository, AdCatalog adCatalog,
              InteractionStore interactionStore, AnalyticsTracker analyticsTracker,
              FeedAdapter adapter, FeedFilterManager filterManager,
              FeedExposureDelegate exposureDelegate,
              RecyclerView recyclerView, SwipeRefreshLayout swipeRefresh,
              ProgressBar loadingView, View statusContainer,
              TextView statusView, View statusRetryHint) {
        this.repository = repository;
        this.adCatalog = adCatalog;
        this.interactionStore = interactionStore;
        this.analyticsTracker = analyticsTracker;
        this.adapter = adapter;
        this.filterManager = filterManager;
        this.exposureDelegate = exposureDelegate;
        this.recyclerView = recyclerView;
        this.swipeRefresh = swipeRefresh;
        this.loadingView = loadingView;
        this.statusContainer = statusContainer;
        this.statusView = statusView;
        this.statusRetryHint = statusRetryHint;
    }

    void onAttach() { attached = true; }
    void onDetach() { attached = false; }

    // ---- 查询 ----

    String getCurrentChannel() { return currentChannel; }
    int getCurrentChannelIndex() { return CHANNELS.indexOf(currentChannel); }

    // ---- 频道切换 ----

    /**
     * 切换到指定频道并刷新数据。
     *
     * @param channelIndex 频道索引（0=精选, 1=电商, 2=本地）
     * @param listener     用于通知 Fragment 更新 Tab UI
     */
    void selectChannel(int channelIndex, OnChannelChangedListener listener) {
        if (channelIndex < 0 || channelIndex >= CHANNELS.size()) {
            return;
        }
        String channel = CHANNELS.get(channelIndex);
        if (!channel.equals(currentChannel)) {
            filterManager.resetAll();
            refreshChannel(channel, true);
        } else {
            // 再次点击当前频道：回到顶部。
            recyclerView.smoothScrollToPosition(0);
        }
        if (listener != null) {
            listener.onChannelChanged(channel);
        }
    }

    // ---- 数据加载 ----

    /**
     * 刷新某个频道的第一页。
     *
     * @param channel        频道名
     * @param showFullLoading true=首屏全屏 loading；false=下拉刷新（用 SwipeRefresh 自带转圈）
     */
    void refreshChannel(String channel, boolean showFullLoading) {
        currentChannel = channel;
        currentPage = 0;
        hasMore = true;
        isLoading = true;

        if (showFullLoading) {
            showLoading();
        }
        hideStatus();
        adapter.setFooterState(FooterState.HIDDEN);

        String channelParam = toChannelParam(channel);
        adCatalog.loadPageWithTags(channelParam, filterManager.getTagFilters(),
                filterManager.getSearchAdIds(), 0, false, new AdCatalog.Callback() {
                    @Override
                    public void onSuccess(FeedPage page) {
                        if (!attached) return;
                        isLoading = false;
                        swipeRefresh.setRefreshing(false);
                        hideLoading();

                        List<AdItem> firstItems = page.getItems();
                        if (firstItems.isEmpty()) {
                            adapter.submit(firstItems);
                            showEmpty();
                            return;
                        }

                        adapter.submit(firstItems);
                        hydratePersistentCounts(firstItems);
                        recyclerView.post(exposureDelegate.getExposureCheckRunnable());
                        hideStatus();
                        hasMore = page.hasMore();
                        adapter.setFooterState(hasMore ? FooterState.HIDDEN : FooterState.NO_MORE);
                        recyclerView.scrollToPosition(0);
                    }

                    @Override
                    public void onError(String message) {
                        if (!attached) return;
                        isLoading = false;
                        swipeRefresh.setRefreshing(false);
                        hideLoading();
                        if (adapter.isEmpty()) {
                            showError(message);
                        }
                    }
                });
    }

    /** 加载下一页（上拉加载更多）。 */
    void loadMore() {
        if (isLoading || !hasMore) return;
        isLoading = true;
        adapter.setFooterState(FooterState.LOADING);

        int nextPage = currentPage + 1;
        boolean failOnPurpose = shouldFailNextLoadMore;
        shouldFailNextLoadMore = false;

        adCatalog.loadPageWithTags(toChannelParam(currentChannel), filterManager.getTagFilters(),
                filterManager.getSearchAdIds(), nextPage, failOnPurpose,
                new AdCatalog.Callback() {
                    @Override
                    public void onSuccess(FeedPage page) {
                        if (!attached) return;
                        isLoading = false;
                        currentPage = nextPage;
                        List<AdItem> moreItems = page.getItems();
                        adapter.append(moreItems);
                        hydratePersistentCounts(moreItems);
                        recyclerView.post(exposureDelegate.getExposureCheckRunnable());
                        hasMore = page.hasMore();
                        adapter.setFooterState(hasMore ? FooterState.HIDDEN : FooterState.NO_MORE);
                    }

                    @Override
                    public void onError(String message) {
                        if (!attached) return;
                        isLoading = false;
                        adapter.setFooterState(FooterState.ERROR);
                    }
                });
    }

    // ---- 内部方法 ----

    /** "精选"当作全部初始流，传空字符串给 AdCatalog。 */
    private String toChannelParam(String channel) {
        return CHANNEL_FEATURED.equals(channel) ? "" : channel;
    }

    /** 从 SQLite 取曝光/点击次数并在后台线程执行，避免主线程 SQLite GROUP BY 查询阻塞。 */
    private void hydratePersistentCounts(List<AdItem> ads) {
        if (ads == null || ads.isEmpty() || analyticsTracker == null) return;
        final List<AdItem> snapshot = new ArrayList<>(ads);
        final int count = snapshot.size();
        backgroundExecutor.execute(() -> {
            Map<String, AdAnalyticsEventCounts> countsByAdId = analyticsTracker.loadCountsByAdId();
            if (countsByAdId.isEmpty()) return;
            mainHandler.post(() -> {
                if (!attached) return;
                for (AdItem ad : snapshot) {
                    AdAnalyticsEventCounts counts = countsByAdId.get(ad.getId());
                    if (counts != null) {
                        interactionStore.applyCounts(ad, counts.getExposureCount(), counts.getClickCount());
                    }
                }
                adapter.notifyItemRangeChanged(0, count);
            });
        });
    }

    // ---- 首屏状态切换 ----

    private void showLoading() {
        loadingView.setVisibility(View.VISIBLE);
        statusContainer.setVisibility(View.GONE);
    }

    private void hideLoading() {
        loadingView.setVisibility(View.GONE);
    }

    private void showEmpty() {
        statusContainer.setVisibility(View.VISIBLE);
        statusView.setText(R.string.feed_empty);
        statusRetryHint.setVisibility(View.VISIBLE);
        statusContainer.setOnClickListener(v -> refreshChannel(currentChannel, true));
    }

    private void showError(String message) {
        statusContainer.setVisibility(View.VISIBLE);
        statusView.setText(message != null ? message : statusView.getContext().getString(R.string.feed_error));
        statusRetryHint.setVisibility(View.VISIBLE);
        statusContainer.setOnClickListener(v -> refreshChannel(currentChannel, true));
    }

    private void hideStatus() {
        statusContainer.setVisibility(View.GONE);
    }
}
