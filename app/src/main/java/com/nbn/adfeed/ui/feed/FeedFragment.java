package com.nbn.adfeed.ui.feed;

import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.nbn.adfeed.analytics.exposure.ExposureTracker;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionState;
import com.nbn.adfeed.data.repository.AdRepository;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 信息流主页面（人员A 核心）。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>单列 RecyclerView 列表布局与滚动；</li>
 *   <li>顶部频道 Tab 切换与联动刷新；</li>
 *   <li>下拉刷新、上拉加载更多；</li>
 *   <li>加载中 / 空态 / 错误态切换；</li>
 *   <li>列表位置保持（详情页返回后恢复滚动位置）；</li>
 *   <li>点赞/收藏/分享交互（含点赞彩蛋动画）与统计上报。</li>
 * </ul>
 *
 * <p>数据来自人员B的 {@link AdRepository}，经 UI 层 {@link AdCatalog} 做分页适配；
 * 统计调用人员C的 {@link AnalyticsTracker}；本类不修改这些模块，只做调用。</p>
 */
public final class FeedFragment extends Fragment implements FeedInteractionListener {

    /** 频道列表，对应课题的“精选 / 电商 / 本地”。 */
    private static final List<String> CHANNELS = Arrays.asList("精选", "电商", "本地");
    /** “精选”视作全部初始流，传空给 AdCatalog。 */
    private static final String CHANNEL_FEATURED = "精选";

    /** 触发上拉加载的预取阈值：距离底部还剩几个时就开始加载下一页。 */
    private static final int PREFETCH_DISTANCE = 2;

    // 依赖（注入或默认实现）。
    private AdCatalog adCatalog;
    private AnalyticsTracker analyticsTracker;
    private final InteractionStore interactionStore = InteractionStore.get();
    // 曝光捕获引入
    private final ExposureTracker exposureTracker = new ExposureTracker();
    //用于在加载、列表滚动、从详情页返回时异步调用曝光检查逻辑
    private final Runnable exposureCheckRunnable = this::checkVisibleExposures;

    // 视图。
    private TextView[] tabButtons;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private ProgressBar loadingView;
    private View statusContainer;
    private TextView statusView;
    private View statusRetryHint;
    private FeedAdapter adapter;

    // 分页与状态。
    private String currentChannel = CHANNEL_FEATURED;
    private int currentPage = 0;
    private boolean hasMore = true;
    private boolean isLoading = false;
    /** 演示用：让下一次加载更多故意失败一次，展示错误态与重试。 */
    private boolean shouldFailNextLoadMore = false;

    // 回调宿主：把搜索入口点击交给 Activity 处理，Feed 不直接依赖搜索模块实现。
    public interface FeedHost {
        void onOpenSearch();
    }

    /**
     * 允许宿主注入 Repository 与统计器，便于复用同一份实例（与搜索页/统计口径一致）。
     * 未注入时使用安全的默认实现。
     */
    public void configure(AdRepository repository, AnalyticsTracker tracker) {
        if (repository != null) {
            this.adCatalog = new AdCatalog(repository);
        }
        this.analyticsTracker = tracker;
    }

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
        if (adCatalog == null) {
            adCatalog = new AdCatalog(new com.nbn.adfeed.data.mock.MockAdRepository());
        }
        if (analyticsTracker == null) {
            analyticsTracker = new AnalyticsTracker();
        }

        bindViews(view);
        setupRecyclerView();
        setupSwipeRefresh();
        setupChannelTabs();
        setupSearchEntry(view);

        // 首次进入加载“精选”频道。
        refreshChannel(currentChannel, true);
    }

    private void bindViews(View view) {
        // 频道胶囊按钮组
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
    }

    private void setupRecyclerView() {
        layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        adapter = new FeedAdapter(this);
        // footer 错误态点击重试：重新加载下一页。
        adapter.setFooterRetryListener(this::loadMore);
        recyclerView.setAdapter(adapter);

        // 监听滚动，接近底部时触发上拉加载更多。
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                //曝光检测
                checkVisibleExposures();
                if (dy <= 0) {
                    return; // 只在向下滚动时考虑预取。
                }
                int totalCount = layoutManager.getItemCount();
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                // 还有更多、当前空闲、滚动到接近底部 -> 加载下一页。
                if (hasMore && !isLoading && lastVisible >= totalCount - 1 - PREFETCH_DISTANCE) {
                    loadMore();
                }
            }
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.nbn_brand);
        // 下拉刷新：重新加载当前频道第一页。
        swipeRefresh.setOnRefreshListener(() -> refreshChannel(currentChannel, false));
    }

    private void setupChannelTabs() {
        // 为每个胶囊按钮绑定点击事件，点击切换频道并更新选中态。
        for (int i = 0; i < tabButtons.length; i++) {
            final int index = i;
            tabButtons[i].setOnClickListener(v -> selectTab(index));
        }
        // 默认选中第一个（精选）。
        selectTab(0);
    }

    /** 切换频道胶囊按钮的选中态，并刷新数据。 */
    private void selectTab(int index) {
        for (int i = 0; i < tabButtons.length; i++) {
            tabButtons[i].setSelected(i == index);
        }
        String channel = CHANNELS.get(index);
        if (!channel.equals(currentChannel)) {
            refreshChannel(channel, true);
        } else {
            // 再次点击当前频道：回到顶部。
            recyclerView.smoothScrollToPosition(0);
        }
    }

    private void setupSearchEntry(View view) {
        view.findViewById(R.id.openSearchButton).setOnClickListener(v -> {
            if (getActivity() instanceof FeedHost) {
                ((FeedHost) getActivity()).onOpenSearch();
            }
        });
    }

    /**
     * 刷新某个频道的第一页。
     *
     * @param channel        频道名
     * @param showFullLoading true=首屏全屏 loading（切频道/首次进入）；false=下拉刷新（用 SwipeRefresh 自带转圈）
     */
    private void refreshChannel(String channel, boolean showFullLoading) {
        currentChannel = channel;
        currentPage = 0;
        hasMore = true;
        isLoading = true;

        // 首屏 loading 与下拉刷新转圈二选一，避免叠加。
        if (showFullLoading) {
            showLoading();
        }
        hideStatus();
        adapter.setFooterState(FooterState.HIDDEN);

        String channelParam = toChannelParam(channel);
        adCatalog.loadPage(channelParam, 0, false, new AdCatalog.Callback() {
            @Override
            public void onSuccess(FeedPage page) {
                if (!isAdded()) {
                    return; // 页面已销毁，丢弃回调，避免 NPE。
                }
                isLoading = false;
                swipeRefresh.setRefreshing(false);
                hideLoading();

                List<AdItem> firstItems = page.getItems();
                if (firstItems.isEmpty()) {
                    adapter.submit(firstItems);
                    showEmpty(); // 空态
                    return;
                }

                adapter.submit(firstItems);
                //曝光检测
                recyclerView.post(exposureCheckRunnable);
                hideStatus();
                hasMore = page.hasMore();
                adapter.setFooterState(hasMore ? FooterState.HIDDEN : FooterState.NO_MORE);
                // 刷新后回到顶部。
                recyclerView.scrollToPosition(0);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                isLoading = false;
                swipeRefresh.setRefreshing(false);
                hideLoading();
                // 首屏失败：列表为空则展示全屏错误态，否则只提示。
                if (adapter.isEmpty()) {
                    showError(message);
                }
            }
        });
    }

    /** 加载下一页（上拉加载更多）。 */
    private void loadMore() {
        if (isLoading || !hasMore) {
            return;
        }
        isLoading = true;
        adapter.setFooterState(FooterState.LOADING);

        int nextPage = currentPage + 1;
        boolean failOnPurpose = shouldFailNextLoadMore;
        shouldFailNextLoadMore = false; // 只失败一次，重试即成功。

        adCatalog.loadPage(toChannelParam(currentChannel), nextPage, failOnPurpose,
                new AdCatalog.Callback() {
                    @Override
                    public void onSuccess(FeedPage page) {
                        if (!isAdded()) {
                            return;
                        }
                        isLoading = false;
                        currentPage = nextPage;
                        adapter.append(page.getItems());
                        //曝光检测，避免新加载出来的广告未记录
                        recyclerView.post(exposureCheckRunnable);
                        hasMore = page.hasMore();
                        adapter.setFooterState(hasMore ? FooterState.HIDDEN : FooterState.NO_MORE);
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) {
                            return;
                        }
                        isLoading = false;
                        // 加载更多失败：footer 显示重试，点击重试再次 loadMore。
                        adapter.setFooterState(FooterState.ERROR);
                    }
                });
    }

    /** “精选”当作全部初始流，传空字符串给 AdCatalog。 */
    private String toChannelParam(String channel) {
        return CHANNEL_FEATURED.equals(channel) ? "" : channel;
    }

    private void checkVisibleExposures() {
        if (recyclerView == null || layoutManager == null || adapter == null) {
            return;
        }

        Map<String, Float> visibleRatios = new HashMap<>();
        Map<String, AdItem> visibleAds = new HashMap<>();
        Map<String, Integer> visiblePositions = new HashMap<>();

        //获取当前可见的广告位置范围
        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
            for (int position = firstVisible; position <= lastVisible; position++) {
                //1、取广告数据
                AdItem ad = adapter.getAdAt(position);
                if (ad == null) {
                    continue;
                }
                //2、根据位置获取ViewHolder
                RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
                if (holder == null) {
                    continue;
                }
                //3、计算ViewHolder可见比例
                float visibleRatio = visibleRatioOf(holder.itemView);
                //4、构建临时数据
                visibleRatios.put(ad.getId(), visibleRatio);
                visibleAds.put(ad.getId(), ad);
                visiblePositions.put(ad.getId(), position);
            }
        }

        long nowMillis = SystemClock.elapsedRealtime();
        //将当前可见广告比例和时间戳传入，返回触发曝光逻辑的id
        List<String> exposedAdIds = exposureTracker.onVisibilityChanged(visibleRatios, nowMillis);
        for (String adId : exposedAdIds) {
            AdItem ad = visibleAds.get(adId);
            Integer position = visiblePositions.get(adId);
            if (ad != null && position != null) {
                //记录曝光数据
                recordExposure(ad, position);
            }
        }
        //安排下一次检查
        scheduleNextExposureCheck(nowMillis);
    }

    private float visibleRatioOf(View itemView) {
        int width = itemView.getWidth();
        int height = itemView.getHeight();
        if (width <= 0 || height <= 0) {
            return 0f;
        }

        Rect visibleRect = new Rect();
        if (!itemView.getGlobalVisibleRect(visibleRect)) {
            return 0f;
        }

        int visibleArea = visibleRect.width() * visibleRect.height();
        int totalArea = width * height;
        if (totalArea <= 0) {
            return 0f;
        }
        return Math.min(1f, (float) visibleArea / (float) totalArea);
    }

    private void recordExposure(AdItem ad, int position) {
        InteractionState state = interactionStore.stateOf(ad);
        state.increaseExposureCount();
        // TODO 目前是内存数据修改，后续异步上传要考虑防止网络IO阻塞
        analyticsTracker.trackExposure(ad.getId());
        //更新RecyclerView上的卡片数据
        adapter.notifyItemChanged(position);
    }

    private void scheduleNextExposureCheck(long nowMillis) {
        recyclerView.removeCallbacks(exposureCheckRunnable);
        long delayMillis = exposureTracker.nextCheckDelayMillis(nowMillis);
        if (delayMillis >= 0L) {
            //延时执行同一个曝光检查
            recyclerView.postDelayed(exposureCheckRunnable, delayMillis);
        }
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
        statusView.setText(message != null ? message : getString(R.string.feed_error));
        statusRetryHint.setVisibility(View.VISIBLE);
        statusContainer.setOnClickListener(v -> refreshChannel(currentChannel, true));
    }

    private void hideStatus() {
        statusContainer.setVisibility(View.GONE);
    }

    // ---- FeedInteractionListener 实现 ----

    @Override
    public void onCardClick(AdItem ad, int position) {
        // 点击卡片计一次点击，并上报统计（人员C 的口径：进入详情计点击）。
        InteractionState state = interactionStore.stateOf(ad);
        state.increaseClickCount();
        analyticsTracker.trackClick(ad.getId());
        adapter.notifyItemChanged(position);

        // 跳转详情页。把展示字段通过 Intent 传过去（AdRepository 暂无按 id 查询接口，
        // 属人员B数据层，这里不改其文件）；互动状态由详情页通过 InteractionStore 按 adId 取共享实例。
        startActivity(com.nbn.adfeed.ui.detail.AdDetailActivity.newIntent(requireContext(), ad));
        // 页面切换动画：右进左出的滑入效果。
        requireActivity().overridePendingTransition(
                android.R.anim.slide_in_left, android.R.anim.fade_out);
    }

    @Override
    public void onLikeClick(AdItem ad, int position) {
        boolean liked = interactionStore.toggleLike(ad);
        // 刷新该项以更新图标颜色与文案。
        adapter.notifyItemChanged(position);
        // 点赞彩蛋：仅在“点亮”时播放一次心形放大动画。
        if (liked) {
            playLikeBurst(position);
        }
    }

    @Override
    public void onCollectClick(AdItem ad, int position) {
        interactionStore.toggleCollect(ad);
        adapter.notifyItemChanged(position);
    }

    @Override
    public void onShareClick(AdItem ad, int position) {
        // 本地模拟分享：弹出系统分享面板（无真实链接，用标题占位），并提示。
        android.content.Intent share = new android.content.Intent(android.content.Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(android.content.Intent.EXTRA_TEXT, ad.getTitle() + " · " + ad.getBrand());
        try {
            startActivity(android.content.Intent.createChooser(share, getString(R.string.feed_action_share)));
        } catch (Exception ignored) {
            // 某些环境无分享目标，降级为 Toast 提示。
        }
        android.widget.Toast.makeText(requireContext(),
                getString(R.string.feed_shared_toast, ad.getTitle()),
                android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onVideoPlayClick(AdItem ad, int position) {
        // 外流视频：点击播放按钮切换播放状态。这里接入人员C的 VideoPlaybackManager
        // 思路（同一时刻只有一个活跃视频），由于本类不持有其实例，先做 UI 占位提示。
        android.widget.Toast.makeText(requireContext(),
                getString(R.string.detail_playing_hint),
                android.widget.Toast.LENGTH_SHORT).show();
    }

    /**
     * 点赞彩蛋动画：在被点赞的心形图标上播放一次放大回弹。
     *
     * <p>从 RecyclerView 找到该 position 的 ViewHolder，取出心形图标做缩放动画。
     * 若该项已滚出屏幕（ViewHolder 为 null），则安全跳过。</p>
     */
    private void playLikeBurst(int position) {
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (!(holder instanceof FeedAdapter.AdViewHolder)) {
            return;
        }
        View likeIcon = holder.itemView.findViewById(R.id.likeIcon);
        if (likeIcon == null) {
            return;
        }
        likeIcon.animate().cancel();
        likeIcon.setScaleX(0.7f);
        likeIcon.setScaleY(0.7f);
        // 放大并回弹，OvershootInterpolator 制造“弹一下”的彩蛋感。
        likeIcon.animate()
                .scaleX(1.25f).scaleY(1.25f)
                .setDuration(160)
                .setInterpolator(new android.view.animation.OvershootInterpolator())
                .withEndAction(() -> likeIcon.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(120)
                        .start())
                .start();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从详情页返回后，互动状态可能在详情页被改过（如点赞），
        // 这里刷新可见项，保证信息流与详情页状态同步。
        if (adapter != null && !adapter.isEmpty()) {
            adapter.notifyDataSetChanged();
            //刷新后补充一次曝光检查
            if (recyclerView != null) {
                recyclerView.post(exposureCheckRunnable);
            }
        }
    }

    
    @Override
    public void onPause() {
        // 离开页面时停止曝光
        if (recyclerView != null) {
            recyclerView.removeCallbacks(exposureCheckRunnable);
        }
        //通过传入空的可见广告取消任务执行
        exposureTracker.onVisibilityChanged(new HashMap<>(), SystemClock.elapsedRealtime());
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        //销毁时停止曝光
        if (recyclerView != null) {
            recyclerView.removeCallbacks(exposureCheckRunnable);
        }
        super.onDestroyView();
    }
}
