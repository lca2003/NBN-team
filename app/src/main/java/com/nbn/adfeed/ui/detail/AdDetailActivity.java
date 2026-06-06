package com.nbn.adfeed.ui.detail;

import android.content.Context;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.ui.PlayerView;

import com.nbn.adfeed.R;
import com.nbn.adfeed.ai.AdAiService;
import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.AdStats;
import com.nbn.adfeed.data.model.InteractionAction;
import com.nbn.adfeed.data.model.InteractionState;
import com.nbn.adfeed.data.repository.AdRepository;
import com.nbn.adfeed.data.repository.RepositoryProvider;
import com.nbn.adfeed.ui.feed.InteractionStore;
import com.nbn.adfeed.ui.feed.TagChipBinder;
import com.nbn.adfeed.ui.media.AdMediaLoader;
import com.nbn.adfeed.ui.media.AdMediaResources;
import com.nbn.adfeed.video.VideoPlaybackManager;
import com.nbn.adfeed.video.player.Media3VideoPlayerController;

import java.util.ArrayList;
import java.util.List;

/**
 * 广告详情页（人员A）。
 *
 * <p>要点：</p>
 * <ul>
 *   <li>展示图文/视频详情；视频默认暂停，点击后用 Media3 播放。</li>
 *   <li>与信息流共享 {@link InteractionStore}：在详情页点赞/收藏后，返回列表自动同步。</li>
 *   <li>通过 {@link AdAiService} 获取 AI 摘要和标签展示。</li>
 *   <li>通过 {@link AdMediaLoader} 加载广告图片。</li>
 *   <li>互动通过 {@link AdRepository#updateInteraction} 上报后端。</li>
 * </ul>
 */
public final class AdDetailActivity extends AppCompatActivity {

    // Intent 传参 key。
    private static final String EXTRA_ID = "extra_ad_id";
    private static final String EXTRA_TITLE = "extra_ad_title";
    private static final String EXTRA_BRAND = "extra_ad_brand";
    private static final String EXTRA_CHANNEL = "extra_ad_channel";
    private static final String EXTRA_SUMMARY = "extra_ad_summary";
    private static final String EXTRA_TYPE = "extra_ad_type";
    private static final String EXTRA_TAGS = "extra_ad_tags";
    private static final String EXTRA_IMAGE_URL = "extra_ad_image_url";
    private static final String EXTRA_THUMBNAIL_URL = "extra_ad_thumbnail_url";
    private static final String EXTRA_VIDEO_URL = "extra_ad_video_url";
    // 素材主题：决定本地兜底图，必须与信息流卡片一致，否则详情页会显示成另一张图。
    private static final String EXTRA_ASSET_THEME = "extra_ad_asset_theme";

    private final InteractionStore interactionStore = InteractionStore.get();
    // 互动上报专用线程池：快，不跟慢速 AI 请求共用，避免点赞/收藏上报被 AI 超时堵住。
    private final java.util.concurrent.ExecutorService interactionExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    // AI 请求专用线程池：摘要与标签可并行跑，互不阻塞。
    private final java.util.concurrent.ExecutorService aiExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(2);

    private AdRepository repository;
    private AdAiService aiService;
    private AnalyticsTracker analyticsTracker;
    private AdItem ad;
    private InteractionState state;
    /** 视频是否处于播放态（由真实播放器驱动 UI 切换）。 */
    private boolean videoPlaying = false;
    /** 成员C的真实视频播放器控制器（内部 ExoPlayer）；仅视频类型时创建。 */
    private Media3VideoPlayerController videoController;
    private PlayerView playerView;

    /** 上次点赞时间（ms），用于连赞窗口判定（1 秒内连点只播动画不取消）。 */
    private long lastLikeTimeMs = 0L;

    // 互动栏视图。
    private ImageView likeIcon;
    private ImageView collectIcon;
    private TextView statsText;

    /**
     * 构造跳转 Intent，把展示字段打包进去。
     *
     * @param context 上下文
     * @param ad      要展示的广告
     */
    public static Intent newIntent(Context context, AdItem ad) {
        Intent intent = new Intent(context, AdDetailActivity.class);
        intent.putExtra(EXTRA_ID, ad.getId());
        intent.putExtra(EXTRA_TITLE, ad.getTitle());
        intent.putExtra(EXTRA_BRAND, ad.getBrand());
        intent.putExtra(EXTRA_CHANNEL, ad.getChannel());
        intent.putExtra(EXTRA_SUMMARY, ad.getSummary());
        intent.putExtra(EXTRA_TYPE, ad.getContentType().name());
        intent.putStringArrayListExtra(EXTRA_TAGS, new ArrayList<>(ad.getTags()));
        // 传递图片和视频 URL，详情页用于加载素材。
        intent.putExtra(EXTRA_IMAGE_URL, ad.getImageUrl());
        intent.putExtra(EXTRA_THUMBNAIL_URL, ad.getThumbnailUrl());
        intent.putExtra(EXTRA_VIDEO_URL, ad.getVideoUrl());
        // 传素材主题，保证详情页兜底图与卡片一致。
        intent.putExtra(EXTRA_ASSET_THEME, ad.getAssetTheme());
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ad_detail);

        // 适配 Android 15+ edge-to-edge：顶部返回栏避开状态栏
        final View detailTopBar = findViewById(R.id.detailTopBar);
        if (detailTopBar != null) {
            final int basePaddingTop = detailTopBar.getPaddingTop();
            ViewCompat.setOnApplyWindowInsetsListener(detailTopBar, (v, insets) -> {
                int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                v.setPadding(v.getPaddingLeft(), basePaddingTop + statusBarHeight,
                        v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        // 获取 Repository 和 AI 服务入口。
        repository = RepositoryProvider.getRepository(this);
        aiService = RepositoryProvider.getAdAiService(this);
        analyticsTracker = new AnalyticsTracker(this);

        ad = parseIntent();
        if (ad == null) {
            finish();
            return;
        }
        // 通过 adId 取与信息流共享的互动状态；若 Store 中尚无（极少数情况），用本页重建的种子。
        InteractionState shared = interactionStore.stateOf(ad.getId());
        state = shared != null ? shared : interactionStore.stateOf(ad);

        bindContent();
        bindInteractions();
        setupBackNavigation();
        loadAiContent();
    }

    /** 使用 OnBackPressedCallback 替代 deprecated onBackPressed。 */
    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithAnimation();
            }
        });
    }

    /** 从 Intent 还原出一个用于展示的 AdItem。 */
    private AdItem parseIntent() {
        Intent intent = getIntent();
        String id = intent.getStringExtra(EXTRA_ID);
        if (id == null) {
            return null;
        }
        List<String> tags = intent.getStringArrayListExtra(EXTRA_TAGS);
        if (tags == null) {
            tags = new ArrayList<>();
        }
        AdContentType type = parseType(intent.getStringExtra(EXTRA_TYPE));
        String imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL);
        String thumbnailUrl = intent.getStringExtra(EXTRA_THUMBNAIL_URL);
        String videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL);
        String title = orEmpty(intent.getStringExtra(EXTRA_TITLE));
        String brand = orEmpty(intent.getStringExtra(EXTRA_BRAND));
        String channel = orEmpty(intent.getStringExtra(EXTRA_CHANNEL));
        String summary = orEmpty(intent.getStringExtra(EXTRA_SUMMARY));
        String assetTheme = orEmpty(intent.getStringExtra(EXTRA_ASSET_THEME));
        // 使用带 assetTheme 的构造函数，保证详情页兜底图与信息流卡片一致。
        return new AdItem(
                id,
                title,
                brand,
                channel,
                channel,       // channelId 与 channel 相同
                summary,       // description 用 summary 代替
                summary,
                imageUrl,
                thumbnailUrl,
                videoUrl,
                "",            // offerText
                "",            // ctaText
                "",            // skuText
                "",            // ratingText
                "",            // deliveryText
                "",            // stockText
                new ArrayList<>(), // similarItems
                "",            // distanceText
                "",            // districtText
                "",            // addressText
                "",            // businessHoursText
                "",            // navigationText
                assetTheme,    // assetTheme：决定兜底图
                "",            // visualLabel
                "",            // ctaIntent
                type,
                tags,
                new InteractionState(),
                AdStats.empty(),
                null
        );
    }

    private static AdContentType parseType(String name) {
        if (name == null) {
            return AdContentType.LARGE_IMAGE;
        }
        try {
            return AdContentType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return AdContentType.LARGE_IMAGE;
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 绑定文本、标签、封面（图文/视频）与统计。 */
    private void bindContent() {
        ((TextView) findViewById(R.id.detailBrand)).setText(ad.getBrand());
        ((TextView) findViewById(R.id.detailTitle)).setText(ad.getTitle());
        ((TextView) findViewById(R.id.detailSummary)).setText(ad.getSummary());

        LinearLayout tagGroup = findViewById(R.id.detailTagGroup);
        TagChipBinder.bind(tagGroup, ad.getTags());

        statsText = findViewById(R.id.detailStats);
        renderStats();

        // 使用 AdMediaLoader 加载详情页图片。
        ImageView detailMedia = findViewById(R.id.detailMedia);
        if (detailMedia != null) {
            AdMediaLoader.loadDetailImage(detailMedia, ad);
        }

        // 视频类型才显示遮罩、播放按钮与状态文案，并接入真实播放器。
        boolean isVideo = ad.getContentType() == AdContentType.VIDEO;
        View scrim = findViewById(R.id.detailVideoScrim);
        ImageView playButton = findViewById(R.id.detailPlayButton);
        TextView videoState = findViewById(R.id.detailVideoState);
        playerView = findViewById(R.id.detailPlayerView);
        String playableUri = AdMediaResources.playableVideoUri(ad);
        boolean playable = isVideo && playableUri != null;
        if (playable) {
            scrim.setVisibility(View.VISIBLE);
            playButton.setVisibility(View.VISIBLE);
            videoState.setVisibility(View.VISIBLE);
            updateVideoState(videoState, scrim, playButton);
            // 不在这里创建 ExoPlayer，改为点击播放时懒初始化，
            // 避免 onCreate 阶段主线程阻塞（ExoPlayer 构造约 100-300ms）。
            playButton.setOnClickListener(v -> startVideoPlayback(playableUri, videoState, scrim, playButton));
        } else {
            // 非视频，或视频缺少有效 URL：隐藏播放相关控件，只展示封面图。
            scrim.setVisibility(View.GONE);
            playButton.setVisibility(View.GONE);
            videoState.setVisibility(View.GONE);
            if (playerView != null) {
                playerView.setVisibility(View.GONE);
            }
        }
    }

    /** 点击播放：显示 PlayerView，隐藏封面与遮罩，调用成员C的控制器真正播放视频。 */
    private void startVideoPlayback(String playableUri, TextView videoState, View scrim, ImageView playButton) {
        if (playerView == null) {
            return;
        }
        // 懒初始化 ExoPlayer：只在用户真正点播放时才创建，避免 onCreate 主线程阻塞。
        if (videoController == null) {
            videoController = new Media3VideoPlayerController(this, new VideoPlaybackManager());
        }
        playerView.setVisibility(View.VISIBLE);
        ImageView detailMedia = findViewById(R.id.detailMedia);
        if (detailMedia != null) {
            detailMedia.setVisibility(View.GONE);
        }
        boolean started = videoController.play(ad.getId(), playableUri, playerView);
        if (started) {
            videoPlaying = true;
            // 进入播放：隐藏遮罩和大播放按钮，交给 PlayerView 自带控制条。
            scrim.setVisibility(View.GONE);
            playButton.setVisibility(View.GONE);
            videoState.setVisibility(View.GONE);
        } else {
            // 播放启动失败：回退到封面态。
            playerView.setVisibility(View.GONE);
            if (detailMedia != null) {
                detailMedia.setVisibility(View.VISIBLE);
            }
            Toast.makeText(this, R.string.detail_video_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    /** 根据播放态更新遮罩、播放按钮与文案。 */
    private void updateVideoState(TextView videoState, View scrim, ImageView playButton) {
        if (videoPlaying) {
            scrim.setVisibility(View.GONE);
            playButton.setVisibility(View.GONE);
            videoState.setText(R.string.detail_playing_hint);
        } else {
            scrim.setVisibility(View.VISIBLE);
            playButton.setVisibility(View.VISIBLE);
            videoState.setText(R.string.detail_pause_hint);
        }
    }

    /** 通过 AdAiService 异步获取 AI 摘要，展示到页面上（标签保持与信息流一致，不做替换）。 */
    private void loadAiContent() {
        if (aiService == null || ad == null) {
            return;
        }
        final String adId = ad.getId();
        aiExecutor.execute(() -> {
            try {
                com.nbn.adfeed.ai.AiResponse<String> summaryResponse = aiService.getAiSummary(adId);
                if (isFinishing() || isDestroyed()) return;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    TextView summaryView = findViewById(R.id.detailSummary);
                    if (summaryResponse != null && summaryResponse.getValue() != null
                            && !summaryResponse.getValue().isEmpty()
                            && !summaryResponse.getValue().equals(summaryView.getText().toString())) {
                        summaryView.setText(summaryResponse.getValue());
                    }
                });
            } catch (Exception ignored) { }
        });
        // 不再异步加载 AI 标签 —— 标签统一使用 bindContent() 中从 Intent 传入的原始标签，
        // 保证详情页与信息流卡片完全一致。
    }

    /** 绑定返回、点赞、收藏、分享。 */
    private void bindInteractions() {
        findViewById(R.id.backButton).setOnClickListener(v -> finishWithAnimation());

        likeIcon = findViewById(R.id.likeIcon);
        collectIcon = findViewById(R.id.collectIcon);
        renderLike();
        renderCollect();

        findViewById(R.id.likeContainer).setOnClickListener(v -> {
            long now = SystemClock.elapsedRealtime();
            // 未点赞 → 点亮 + 爱心 + 记录时间
            if (!state.isLiked()) {
                state.setLiked(true);
                reportInteraction(InteractionAction.TOGGLE_LIKE);
                renderLike();
                lastLikeTimeMs = now;
                playLikeBurst();
                return;
            }
            // 已点赞且 1 秒内连点 → 只弹爱心
            if (now - lastLikeTimeMs < 1000L) {
                renderLike();
                playLikeBurst();
                return;
            }
            // 超过 1 秒 → 取消点赞
            state.setLiked(false);
            reportInteraction(InteractionAction.TOGGLE_LIKE);
            if (liked) {
                analyticsTracker.trackLike(ad.getId());
            } else {
                analyticsTracker.trackUnlike(ad.getId());
            }
            renderLike();
        });
        findViewById(R.id.collectContainer).setOnClickListener(v -> {
            boolean collected = interactionStore.toggleCollect(ad);
            // 后台线程上报后端。
            reportInteraction(InteractionAction.TOGGLE_COLLECT);
            if (collected) {
                analyticsTracker.trackCollect(ad.getId());
            } else {
                analyticsTracker.trackUncollect(ad.getId());
            }
            renderCollect();
        });
        findViewById(R.id.shareContainer).setOnClickListener(v -> {
            // 后台线程上报后端。
            reportInteraction(InteractionAction.SHARE);
            analyticsTracker.trackShare(ad.getId());
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, ad.getTitle() + " · " + ad.getBrand());
            try {
                startActivity(Intent.createChooser(share, getString(R.string.feed_action_share)));
            } catch (Exception ignored) {
                // 无分享目标时降级提示。
            }
            Toast.makeText(this, getString(R.string.feed_shared_toast, ad.getTitle()),
                    Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 在后台线程上报互动事件，避免主线程网络调用导致 NetworkOnMainThreadException。
     */
    private void reportInteraction(InteractionAction action) {
        if (ad == null || repository == null) {
            return;
        }
        String adId = ad.getId();
        interactionExecutor.execute(() -> {
            try {
                repository.updateInteraction(adId, action);
            } catch (Exception ignored) {
                // 上报失败不影响 UI，本地 InteractionStore 已即时刷新。
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 离开页面（返回/切后台）时暂停视频，避免在不可见时继续播放。
        if (videoController != null && videoPlaying) {
            videoController.pauseActive();
        }
    }

    @Override
    protected void onDestroy() {
        // 释放播放器，回收 ExoPlayer 资源，避免内存泄漏与后台播放。
        if (videoController != null) {
            videoController.release();
            videoController = null;
        }
        // 关闭后台线程池，避免 Activity 销毁后线程泄漏。
        interactionExecutor.shutdown();
        aiExecutor.shutdown();
        super.onDestroy();
    }

    private void renderStats() {
        statsText.setText(getString(R.string.feed_stats_format,
                state.getExposureCount(), state.getClickCount()));
    }

    private void renderLike() {
        int color = state.isLiked()
                ? getColor(R.color.nbn_like_active)
                : getColor(R.color.nbn_text_secondary);
        likeIcon.setColorFilter(color);
    }

    private void renderCollect() {
        int color = state.isCollected()
                ? getColor(R.color.nbn_collect_active)
                : getColor(R.color.nbn_text_secondary);
        collectIcon.setColorFilter(color);
    }

    /**
     * 抖音式连赞特效：爱心从点赞按钮位置弹出，向上飘动 + 随机旋转 + 放大 + 淡出。
     * 每次点击独立创建爱心，互不干扰，支持快速连点多个爱心同时飘。
     */
    private void playLikeBurst() {
        View root = getWindow().getDecorView().findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) root;

        float density = root.getResources().getDisplayMetrics().density;
        int size = (int) (density * 80);

        // 爱心起始于 likeIcon 位置
        int[] loc = new int[2];
        likeIcon.getLocationOnScreen(loc);
        int[] rootLoc = new int[2];
        root.getLocationOnScreen(rootLoc);
        float startX = loc[0] - rootLoc[0] + likeIcon.getWidth() / 2f - size / 2f;
        float startY = loc[1] - rootLoc[1] - size / 2f;

        ImageView heart = new ImageView(this);
        heart.setImageResource(R.drawable.ic_like);
        heart.setColorFilter(getColor(R.color.nbn_like_active));
        heart.setAlpha(0f);
        heart.setScaleX(0.4f);
        heart.setScaleY(0.4f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.leftMargin = (int) startX;
        params.topMargin = (int) startY;
        heart.setLayoutParams(params);
        parent.addView(heart);

        float riseDistance = -(density * (180 + (float) Math.random() * 120));
        float driftX = density * ((float) (Math.random() - 0.5) * 80);
        float rotation = (float) ((Math.random() - 0.5) * 60);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(heart, "scaleX", 0.4f, 1.2f, 1.5f),
                ObjectAnimator.ofFloat(heart, "scaleY", 0.4f, 1.2f, 1.5f),
                ObjectAnimator.ofFloat(heart, "alpha", 0f, 1f, 1f, 0.4f, 0f),
                ObjectAnimator.ofFloat(heart, "translationY", 0f, riseDistance * 0.6f, riseDistance),
                ObjectAnimator.ofFloat(heart, "translationX", 0f, driftX * 0.3f, driftX),
                ObjectAnimator.ofFloat(heart, "rotation", 0f, rotation * 0.4f, rotation)
        );
        set.setDuration(1000);
        set.setInterpolator(new AccelerateInterpolator(0.8f));

        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                parent.removeView(heart);
            }
        });
        set.start();
    }

    /** 关闭页面并播放"左进右出"返回动画，与进入时的滑入呼应。 */
    private void finishWithAnimation() {
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right);
    }
}
