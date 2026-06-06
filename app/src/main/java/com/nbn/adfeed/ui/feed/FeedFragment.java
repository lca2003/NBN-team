package com.nbn.adfeed.ui.feed;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
 * 信息流主页面（人员A 核心），薄协调层。
 *
 * <p>委托给以下组件：</p>
 * <ul>
 *   <li>{@link FeedExposureDelegate} — 曝光检测、可见比例计算、定时调度</li>
 *   <li>{@link FeedInteractionDelegate} — 卡片交互（点赞/收藏/分享/点击/视频播放）与彩蛋动画</li>
 *   <li>{@link FeedFilterManager} — 标签筛选状态、搜索过滤、筛选栏 UI</li>
 *   <li>{@link FeedDataController} — 频道切换、分页加载、首屏状态切换</li>
 *   <li>{@link FeedSwipeChannelDelegate} — 左右滑动手势切换频道</li>
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
    private final FeedSwipeChannelDelegate swipeChannelDelegate = new FeedSwipeChannelDelegate();

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

    /** 滚动曝光检测节流 */
    private static final long EXPOSURE_THROTTLE_MS = 200L;
    private long lastExposureCheckMs = 0L;

    // ---- 回调宿主 ----

    /** 把搜索入口点击交给 Activity 处理，Feed 不直接依赖搜索模块实现。 */
    public interface FeedHost {
        void onOpenSearch();
    }

    /**
     * 允许宿主注入 Repository 与统计器，便于复用同一份实例。
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
        if (adapter != null && !adapter.isEmpty()) {
            int first = layoutManager.findFirstVisibleItemPosition();
            int last = layoutManager.findLastVisibleItemPosition();
            if (first != RecyclerView.NO_POSITION && last != RecyclerView.NO_POSITION) {
                adapter.notifyItemRangeChanged(first, last - first + 1);
            } else {
                adapter.notifyDataSetChanged();
            }
            if (recyclerView != null) {
                recyclerView.post(exposureDelegate.getExposureCheckRunnable());
            }
        }
    }

    @Override
    public void onPause() {
        interactionDelegate.pauseActiveVideo();
        exposureDelegate.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
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
        adapter.setFooterRetryListener(dataController::loadMore);
        recyclerView.setAdapter(adapter);

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
                rv.post(() -> {
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastExposureCheckMs >= EXPOSURE_THROTTLE_MS) {
                        lastExposureCheckMs = now;
                        exposureDelegate.checkVisibleExposures();
                    }
                    if (dy <= 0) return;
                    int totalCount = layoutManager.getItemCount();
                    int lastVisible = layoutManager.findLastVisibleItemPosition();
                    if (lastVisible >= totalCount - 1 - 2) {
                        dataController.loadMore();
                    }
                });
            }
        });

        // 鼠标滚轮支持
        final float density = getResources().getDisplayMetrics().density;
        recyclerView.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_SCROLL) {
                float vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (vScroll != 0f) {
                    recyclerView.scrollBy(0, Math.round(-vScroll * density * 64));
                    return true;
                }
            }
            return false;
        });
    }

    /** 将五个委托与依赖、视图连接起来。 */
    private void wireDelegates() {
        exposureDelegate.bind(recyclerView, layoutManager, adapter,
                interactionStore, adCatalog, analyticsTracker);

        interactionDelegate.bind(requireContext(), recyclerView, adapter,
                interactionStore, adCatalog, analyticsTracker);

        VideoPlaybackManager videoPlaybackManager = new VideoPlaybackManager();
        Media3VideoPlayerController videoController = new Media3VideoPlayerController(
                requireContext(), videoPlaybackManager);
        interactionDelegate.bindVideo(videoPlaybackManager, videoController);

        filterManager.bind(requireContext(), adapter, filterBarContainer, filterBar);
        filterManager.setOnFilterChangedListener(() ->
                dataController.refreshChannel(dataController.getCurrentChannel(), true));

        dataController.bind(repository, adCatalog, interactionStore, analyticsTracker,
                adapter, filterManager, exposureDelegate,
                recyclerView, swipeRefresh, loadingView,
                statusContainer, statusView, statusRetryHint);

        // 手势频道切换委托
        swipeChannelDelegate.bind(recyclerView, dataController, tabButtons);
        swipeChannelDelegate.attach();
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.nbn_brand);
        swipeRefresh.setOnRefreshListener(() ->
                dataController.refreshChannel(dataController.getCurrentChannel(), false));
    }

    private void setupChannelTabs() {
        for (int i = 0; i < tabButtons.length; i++) {
            final int index = i;
            tabButtons[i].setOnClickListener(v -> {
                for (int j = 0; j < tabButtons.length; j++) {
                    tabButtons[j].setSelected(j == index);
                }
                dataController.selectChannel(index, null);
            });
        }
        tabButtons[0].setSelected(true);
    }

    private void setupSearchEntry(View view) {
        int searchButtonId = getResources().getIdentifier(
                "openSearchButton", "id", requireContext().getPackageName());
        if (searchButtonId == 0) return;
        View openSearchButton = view.findViewById(searchButtonId);
        if (openSearchButton == null) return;
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
        filterManager.toggleTag(tag);
    }

    @Override
    public void onVideoPlayClick(AdItem ad, int position) {
        interactionDelegate.onVideoPlayClick(ad, position);
    }

    @Override
    public void onVideoCardDetached(AdItem ad) {
        interactionDelegate.onVideoCardDetached(ad);
    }
}
