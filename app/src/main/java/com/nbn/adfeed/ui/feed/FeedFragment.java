package com.nbn.adfeed.ui.feed;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.nbn.adfeed.R;
import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.repository.AdRepository;
import com.nbn.adfeed.video.VideoPlaybackManager;
import com.nbn.adfeed.video.player.Media3VideoPlayerController;

import java.util.List;

/**
 * 信息流主页面（人员A 核心）。
 *
 * <p>本类作为薄协调层，将具体职责委托给：</p>
 * <ul>
 *   <li>{@link FeedExposureDelegate} — 曝光检测、可见比例计算、定时调度</li>
 *   <li>{@link FeedInteractionDelegate} — 卡片交互（点赞/收藏/分享/点击/视频播放）与彩蛋动画</li>
 *   <li>{@link FeedFilterManager} — 标签筛选状态、搜索过滤、筛选栏 UI</li>
 *   <li>{@link FeedDataController} — 频道切换、分页加载、首屏状态切换</li>
 * </ul>
 *
 * <p>数据来自人员B的 {@link AdRepository}，经 UI 层 {@link AdCatalog} 做分页适配；
 * 统计调用人员C的 {@link AnalyticsTracker}；本类不修改这些模块，只做调用。</p>
 */
public final class FeedFragment extends Fragment implements FeedInteractionListener {

    // ---- 委托 ----
    private final FeedExposureDelegate exposureDelegate = new FeedExposureDelegate();
    private final FeedInteractionDelegate interactionDelegate = new FeedInteractionDelegate();
    private final FeedFilterManager filterManager = new FeedFilterManager();
    private final FeedDataController dataController = new FeedDataController();

    // ---- 依赖（注入或默认实现） ----
    private AdRepository repository;
    private AdCatalog adCatalog;
    private AnalyticsTracker analyticsTracker;
    private final InteractionStore interactionStore = InteractionStore.get();

    // ---- 视图 ----
    private TextView[] tabButtons;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private ProgressBar loadingView;
    private View statusContainer;
    private TextView statusView;
    private View statusRetryHint;
    private FeedAdapter adapter;
    private View filterBarContainer;
    private LinearLayout filterBar;
    private View filterClearAll;

    /** 滚动曝光检测节流：距上次检测不足此值则跳过，避免 fling 期间每秒 ~60 次冗余检测。 */
    private static final long EXPOSURE_THROTTLE_MS = 200L;
    private long lastExposureCheckMs = 0L;

    // ---- 回调宿主 ----

    /** 把搜索入口点击交给 Activity 处理，Feed 不直接依赖搜索模块实现。 */
    public interface FeedHost {
        void onOpenSearch();
    }

    /**
     * 允许宿主注入 Repository 与统计器，便于复用同一份实例（与搜索页/统计口径一致）。
     * 未注入时使用安全的默认实现。
     */
    public void configure(AdRepository repository, AnalyticsTracker tracker) {
        if (repository != null) {
            this.repository = repository;
            this.adCatalog = new AdCatalog(repository);
        }
        this.analyticsTracker = tracker;
    }

    // ---- 生命周期 ----

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 兜底：若宿主未注入依赖，使用默认实例，保证 Fragment 可独立运行。
        if (repository == null) {
            repository = new com.nbn.adfeed.data.mock.MockAdRepository();
        }
        if (adCatalog == null) {
            adCatalog = new AdCatalog(repository);
        }
        if (analyticsTracker == null) {
            analyticsTracker = new AnalyticsTracker(requireContext());
        }

        bindViews(view);
        setupRecyclerView();
        wireDelegates();
        setupSwipeRefresh();
        setupChannelTabs();
        setupSearchEntry(view);
        wireFilterClearButton();

        dataController.onAttach();

        // 首次进入加载"精选"频道。
        dataController.refreshChannel(FeedDataController.CHANNELS.get(0), true);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从详情页返回后，互动状态可能在详情页被改过（如点赞），
        // 这里只刷新当前可见范围内的项，避免全部 rebind。
        if (adapter != null && !adapter.isEmpty()) {
            int first = layoutManager.findFirstVisibleItemPosition();
            int last = layoutManager.findLastVisibleItemPosition();
            if (first != RecyclerView.NO_POSITION && last != RecyclerView.NO_POSITION) {
                adapter.notifyItemRangeChanged(first, last - first + 1);
            } else {
                adapter.notifyDataSetChanged();
            }
            // 刷新后补充一次曝光检查。
            if (recyclerView != null) {
                recyclerView.post(exposureDelegate.getExposureCheckRunnable());
            }
        }
    }

    @Override
    public void onPause() {
        // 离开页面时暂停信息流中的视频播放并停止曝光调度。
        interactionDelegate.pauseActiveVideo();
        exposureDelegate.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        // 销毁时释放视频播放器资源、停止曝光调度、解除 DataController 标记。
        interactionDelegate.releaseVideo();
        exposureDelegate.onDestroyView();
        dataController.onDetach();
        super.onDestroyView();
    }

    // ---- 视图初始化 ----

    private void bindViews(View view) {
        tabButtons = new TextView[]{
                view.findViewById(R.id.tabFeatured),
                view.findViewById(R.id.tabEcommerce),
                view.findViewById(R.id.tabLocal)
        };
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        recyclerView = view.findViewById(R.id.feedRecyclerView);
        loadingView = view.findViewById(R.id.loadingView);
        statusContainer = view.findViewById(R.id.statusContainer);
        statusView = view.findViewById(R.id.statusView);
        statusRetryHint = view.findViewById(R.id.statusRetryHint);
        filterBarContainer = view.findViewById(R.id.filterBarContainer);
        filterBar = view.findViewById(R.id.filterBar);
        filterClearAll = view.findViewById(R.id.filterClearAll);
    }

    private void setupRecyclerView() {
        layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        adapter = new FeedAdapter(this);
        adapter.setFooterRetryListener(() -> dataController.loadMore());
        recyclerView.setAdapter(adapter);

        // 监听滚动：接近底部时触发上拉加载更多，并执行曝光检测。
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    rv.post(exposureDelegate.getExposureCheckRunnable());
                    rv.post(() -> interactionDelegate.autoPlayMostVisibleVideo(layoutManager));
                } else {
                    interactionDelegate.pauseActiveVideo();
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                // 滚动回调期间不能直接修改 adapter 数据，推迟到下一帧执行。
                rv.post(() -> {
                    // fling 期间节流曝光检测：200ms 内只做一次，ExposureTracker 的 1000ms
                    // dwell 阈值确保不会遗漏曝光。
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastExposureCheckMs >= EXPOSURE_THROTTLE_MS) {
                        lastExposureCheckMs = now;
                        exposureDelegate.checkVisibleExposures();
                    }
                    if (dy <= 0) {
                        return; // 只在向下滚动时考虑预取。
                    }
                    int totalCount = layoutManager.getItemCount();
                    int lastVisible = layoutManager.findLastVisibleItemPosition();
                    // 触发上拉加载的预取阈值：距离底部还剩 2 个时开始加载下一页。
                    if (lastVisible >= totalCount - 1 - 2) {
                        dataController.loadMore();
                    }
                });
            }
        });
    }

    /**
     * 将四个委托与依赖、视图连接起来。
     * 必须在 {@link #setupRecyclerView()} 之后调用（需要 adapter 和 layoutManager）。
     */
    private void wireDelegates() {
        // 曝光委托：管理广告可见性检测与曝光记录。
        exposureDelegate.bind(recyclerView, layoutManager, adapter,
                interactionStore, adCatalog, analyticsTracker);

        // 交互委托：处理点赞/收藏/分享/卡片点击。
        interactionDelegate.bind(requireContext(), recyclerView, adapter,
                interactionStore, adCatalog, analyticsTracker);

        // 视频播放：创建成员C的控制器（同一时刻只有一个活跃视频）。
        VideoPlaybackManager videoPlaybackManager = new VideoPlaybackManager();
        Media3VideoPlayerController videoController = new Media3VideoPlayerController(
                requireContext(), videoPlaybackManager);
        interactionDelegate.bindVideo(videoPlaybackManager, videoController);

        // 筛选管理器：管理标签筛选状态与筛选栏 UI。
        filterManager.bind(requireContext(), adapter, filterBarContainer, filterBar);
        filterManager.setOnFilterChangedListener(() ->
                dataController.refreshChannel(dataController.getCurrentChannel(), true));

        // 数据控制器：管理频道切换、分页加载、首屏状态。
        dataController.bind(repository, adCatalog, interactionStore, analyticsTracker,
                adapter, filterManager, exposureDelegate,
                recyclerView, swipeRefresh, loadingView,
                statusContainer, statusView, statusRetryHint);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.nbn_brand);
        // 下拉刷新：重新加载当前频道第一页。
        swipeRefresh.setOnRefreshListener(() ->
                dataController.refreshChannel(dataController.getCurrentChannel(), false));
    }

    private void setupChannelTabs() {
        // 为每个胶囊按钮绑定点击事件。
        for (int i = 0; i < tabButtons.length; i++) {
            final int index = i;
            tabButtons[i].setOnClickListener(v -> {
                // 更新 Tab UI 选中态。
                for (int j = 0; j < tabButtons.length; j++) {
                    tabButtons[j].setSelected(j == index);
                }
                // 委托 DataController 处理频道切换与数据刷新。
                dataController.selectChannel(index, null);
            });
        }
        // 默认选中第一个（精选）。
        tabButtons[0].setSelected(true);
    }

    private void setupSearchEntry(View view) {
        // 搜索入口已迁移到底部导航，由 MainActivity 承载；这里兼容旧布局中仍存在入口按钮的情况。
        int searchButtonId = getResources().getIdentifier(
                "openSearchButton", "id", requireContext().getPackageName());
        if (searchButtonId == 0) {
            return;
        }
        View openSearchButton = view.findViewById(searchButtonId);
        if (openSearchButton == null) {
            return;
        }
        openSearchButton.setOnClickListener(v -> {
            if (getActivity() instanceof FeedHost) {
                ((FeedHost) getActivity()).onOpenSearch();
            }
        });
    }

    private void wireFilterClearButton() {
        filterClearAll.setOnClickListener(v -> filterManager.clearAll());
    }

    // ---- 公开方法 ----

    /** 应用搜索结果刷新界面。 */
    public void applySearchResult(List<String> matchedAdIds) {
        filterManager.applySearchResult(matchedAdIds);
        if (adapter != null) {
            dataController.refreshChannel(dataController.getCurrentChannel(), true);
        }
    }

    // ---- FeedInteractionListener 实现 ----
    // 每个方法委托给对应的组件，Fragment 作为纯协调层。

    @Override
    public void onCardClick(AdItem ad, int position) {
        interactionDelegate.onCardClick(ad, position);
    }

    @Override
    public void onLikeClick(AdItem ad, int position) {
        interactionDelegate.onLikeClick(ad, position);
    }

    @Override
    public void onCollectClick(AdItem ad, int position) {
        interactionDelegate.onCollectClick(ad, position);
    }

    @Override
    public void onShareClick(AdItem ad, int position) {
        interactionDelegate.onShareClick(ad, position);
    }

    @Override
    public void onTagClick(AdItem ad, String tag, int position) {
        // 标签点击由 FilterManager 管理状态并自动触发 onFilterChanged → 数据刷新。
        filterManager.toggleTag(tag);
    }

    @Override
    public void onVideoPlayClick(AdItem ad, int position) {
        interactionDelegate.onVideoPlayClick(ad, position);
    }
}
