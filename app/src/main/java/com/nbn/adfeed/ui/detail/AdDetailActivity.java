package com.nbn.adfeed.ui.detail;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.nbn.adfeed.R;
import com.nbn.adfeed.ai.AdAiService;
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

import java.util.ArrayList;
import java.util.List;

/**
 * 广告详情页（人员A）。
 *
 * <p>要点：</p>
 * <ul>
 *   <li>展示图文/视频详情；视频默认暂停，点击播放（演示占位）。</li>
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

    private final InteractionStore interactionStore = InteractionStore.get();
    // 后台线程池：互动上报和 AI 请求都走 repository/service 的 HTTP 调用，不能在主线程执行。
    private final java.util.concurrent.ExecutorService executor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    private AdRepository repository;
    private AdAiService aiService;
    private AdItem ad;
    private InteractionState state;
    /** 视频是否处于播放态（演示占位状态，无真实播放器）。 */
    private boolean videoPlaying = false;

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
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ad_detail);

        // 获取 Repository 和 AI 服务入口。
        repository = RepositoryProvider.getRepository(this);
        aiService = RepositoryProvider.getAdAiService(this);

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
        // 使用 15 参数构造函数，传入 imageUrl/thumbnailUrl/videoUrl。
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

        // 视频类型才显示遮罩、播放按钮与状态文案。
        boolean isVideo = ad.getContentType() == AdContentType.VIDEO;
        View scrim = findViewById(R.id.detailVideoScrim);
        ImageView playButton = findViewById(R.id.detailPlayButton);
        TextView videoState = findViewById(R.id.detailVideoState);
        if (isVideo) {
            scrim.setVisibility(View.VISIBLE);
            playButton.setVisibility(View.VISIBLE);
            videoState.setVisibility(View.VISIBLE);
            updateVideoState(videoState, scrim, playButton);
            playButton.setOnClickListener(v -> {
                // 切换播放/暂停（演示占位，无真实播放器）。
                videoPlaying = !videoPlaying;
                updateVideoState(videoState, scrim, playButton);
            });
        } else {
            scrim.setVisibility(View.GONE);
            playButton.setVisibility(View.GONE);
            videoState.setVisibility(View.GONE);
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

    /** 通过 AdAiService 异步获取 AI 摘要和标签，展示到页面上。 */
    private void loadAiContent() {
        if (aiService == null || ad == null) {
            return;
        }
        // 在后台线程执行 AI 请求，避免阻塞主线程。
        new Thread(() -> {
            try {
                // 获取 AI 摘要。
                com.nbn.adfeed.ai.AiResponse<String> summaryResponse = aiService.getAiSummary(ad.getId());
                // 获取 AI 标签。
                com.nbn.adfeed.ai.AiResponse<List<String>> tagsResponse = aiService.getAiTags(ad.getId());

                runOnUiThread(() -> {
                    // 更新摘要（如果 AI 返回了有效内容）。
                    if (summaryResponse != null && summaryResponse.getValue() != null
                            && !summaryResponse.getValue().isEmpty()) {
                        TextView summaryView = findViewById(R.id.detailSummary);
                        summaryView.setText(summaryResponse.getValue());
                    }
                    // 更新标签（如果 AI 返回了有效标签列表）。
                    if (tagsResponse != null && tagsResponse.getValue() != null
                            && !tagsResponse.getValue().isEmpty()) {
                        LinearLayout tagGroup = findViewById(R.id.detailTagGroup);
                        TagChipBinder.bind(tagGroup, tagsResponse.getValue());
                    }
                });
            } catch (Exception e) {
                // AI 服务失败不阻塞 UI，继续展示 Intent 传入的原始数据。
            }
        }).start();
    }

    /** 绑定返回、点赞、收藏、分享。 */
    private void bindInteractions() {
        findViewById(R.id.backButton).setOnClickListener(v -> finishWithAnimation());

        likeIcon = findViewById(R.id.likeIcon);
        collectIcon = findViewById(R.id.collectIcon);
        renderLike();
        renderCollect();

        findViewById(R.id.likeContainer).setOnClickListener(v -> {
            boolean liked = interactionStore.toggleLike(ad);
            // 后台线程上报后端，避免主线程网络调用。
            reportInteraction(InteractionAction.TOGGLE_LIKE);
            renderLike();
            if (liked) {
                playLikeBurst();
            }
        });
        findViewById(R.id.collectContainer).setOnClickListener(v -> {
            interactionStore.toggleCollect(ad);
            // 后台线程上报后端。
            reportInteraction(InteractionAction.TOGGLE_COLLECT);
            renderCollect();
        });
        findViewById(R.id.shareContainer).setOnClickListener(v -> {
            // 后台线程上报后端。
            reportInteraction(InteractionAction.SHARE);
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
        executor.execute(() -> {
            try {
                repository.updateInteraction(adId, action);
            } catch (Exception ignored) {
                // 上报失败不影响 UI，本地 InteractionStore 已即时刷新。
            }
        });
    }

    @Override
    protected void onDestroy() {
        // 关闭后台线程池，避免 Activity 销毁后线程泄漏。
        executor.shutdown();
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

    /** 点赞彩蛋：心形放大回弹一次。 */
    private void playLikeBurst() {
        likeIcon.animate().cancel();
        likeIcon.setScaleX(0.7f);
        likeIcon.setScaleY(0.7f);
        likeIcon.animate()
                .scaleX(1.3f).scaleY(1.3f)
                .setDuration(160)
                .setInterpolator(new android.view.animation.OvershootInterpolator())
                .withEndAction(() -> likeIcon.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                .start();
    }

    /** 关闭页面并播放"左进右出"返回动画，与进入时的滑入呼应。 */
    private void finishWithAnimation() {
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right);
    }
}
