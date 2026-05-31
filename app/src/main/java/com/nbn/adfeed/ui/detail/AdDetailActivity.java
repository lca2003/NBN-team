package com.nbn.adfeed.ui.detail;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.nbn.adfeed.R;
import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionState;
import com.nbn.adfeed.ui.feed.InteractionStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 广告详情页（人员A）。
 *
 * <p>要点：</p>
 * <ul>
 *   <li>展示图文/视频详情；视频默认暂停，点击播放（演示占位）。</li>
 *   <li>与信息流共享 {@link InteractionStore}：在详情页点赞/收藏后，返回列表自动同步。</li>
 *   <li>返回时列表位置由 RecyclerView 在 Activity 栈切换中保持，配合返回动画。</li>
 * </ul>
 *
 * <p>说明：当前 {@link com.nbn.adfeed.data.repository.AdRepository} 没有“按 id 查询单条广告”的接口
 * （属人员B数据层），因此展示字段通过 Intent 传入；互动状态通过 {@link InteractionStore} 按 adId
 * 取到与信息流同一份实例，实现跨页同步。等数据层补充按 id 查询后可改为只传 id。</p>
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

    private final InteractionStore interactionStore = InteractionStore.get();

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
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ad_detail);

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
        return new AdItem(
                id,
                orEmpty(intent.getStringExtra(EXTRA_TITLE)),
                orEmpty(intent.getStringExtra(EXTRA_BRAND)),
                orEmpty(intent.getStringExtra(EXTRA_CHANNEL)),
                orEmpty(intent.getStringExtra(EXTRA_SUMMARY)),
                type,
                tags,
                new InteractionState()
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
        com.nbn.adfeed.ui.feed.TagChipBinder.bind(tagGroup, ad.getTags());

        statsText = findViewById(R.id.detailStats);
        renderStats();

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
            // 播放中：隐藏遮罩与播放按钮，仅显示“播放中”。
            scrim.setVisibility(View.GONE);
            playButton.setVisibility(View.GONE);
            videoState.setText(R.string.detail_playing_hint);
        } else {
            scrim.setVisibility(View.VISIBLE);
            playButton.setVisibility(View.VISIBLE);
            videoState.setText(R.string.detail_pause_hint);
        }
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
            renderLike();
            if (liked) {
                playLikeBurst();
            }
        });
        findViewById(R.id.collectContainer).setOnClickListener(v -> {
            interactionStore.toggleCollect(ad);
            renderCollect();
        });
        findViewById(R.id.shareContainer).setOnClickListener(v -> {
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

    /** 系统返回键也走带动画的返回，保证返回信息流时的过渡一致。 */
    @Override
    public void onBackPressed() {
        finishWithAnimation();
    }

    /** 关闭页面并播放“左进右出”返回动画，与进入时的滑入呼应。 */
    private void finishWithAnimation() {
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right);
    }
}