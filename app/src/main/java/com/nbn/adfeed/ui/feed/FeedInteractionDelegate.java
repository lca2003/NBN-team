package com.nbn.adfeed.ui.feed;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.nbn.adfeed.R;
import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionAction;
import com.nbn.adfeed.data.model.InteractionState;
import com.nbn.adfeed.video.VideoPlaybackManager;
import com.nbn.adfeed.video.player.Media3VideoPlayerController;

/**
 * 交互处理委托，从 FeedFragment 中提取。
 *
 * <p>职责：处理点赞/收藏/分享/点击/视频播放事件，以及点赞彩蛋动画。</p>
 */
final class FeedInteractionDelegate {

    // 外部依赖。
    private Context context;
    private RecyclerView recyclerView;
    private FeedAdapter adapter;
    private InteractionStore interactionStore;
    private AdCatalog adCatalog;
    private AnalyticsTracker analyticsTracker;

    // 视频播放（成员C的控制器，同一时刻只有一个活跃视频）。
    private VideoPlaybackManager videoPlaybackManager;
    private Media3VideoPlayerController videoController;
    /** 当前正在信息流中播放的视频广告 ID，用于恢复 UI。null 表示无活跃播放。 */
    private String playingAdId;
    /** 当前播放中卡片在 RecyclerView 中的位置。 */
    private int playingPosition = RecyclerView.NO_POSITION;

    /** 上次点赞时间（ms），用于连赞窗口判定（1 秒内连点只播动画不取消）。 */
    private long lastLikeTimeMs = 0L;

    /**
     * 绑定外部依赖。在 Fragment.onViewCreated 之后调用。
     */
    void bind(Context context,
              RecyclerView recyclerView,
              FeedAdapter adapter,
              InteractionStore interactionStore,
              AdCatalog adCatalog,
              AnalyticsTracker analyticsTracker) {
        this.context = context;
        this.recyclerView = recyclerView;
        this.adapter = adapter;
        this.interactionStore = interactionStore;
        this.adCatalog = adCatalog;
        this.analyticsTracker = analyticsTracker;
    }

    /**
     * 绑定视频播放依赖（成员C的控制器）。
     * 必须在 {@link #bind} 之后调用。
     */
    void bindVideo(VideoPlaybackManager videoPlaybackManager,
                   Media3VideoPlayerController videoController) {
        this.videoPlaybackManager = videoPlaybackManager;
        this.videoController = videoController;
        if (videoController != null) {
            videoController.setPlaybackCallback(new Media3VideoPlayerController.PlaybackCallback() {
                @Override
                public void onBuffering(String adId) { }

                @Override
                public void onPlaying(String adId) { }

                @Override
                public void onPaused(String adId) { }

                @Override
                public void onEnded(String adId) {
                    restorePlayingCardUi();
                    // 视频自然播完后重置进度，确保下次点击从头播放。
                    if (videoPlaybackManager != null && adId != null) {
                        videoPlaybackManager.updatePositionMs(adId, 0L);
                    }
                }

                @Override
                public void onError(String adId, String message) {
                    restorePlayingCardUi();
                    if (videoPlaybackManager != null && adId != null) {
                        videoPlaybackManager.updatePositionMs(adId, 0L);
                    }
                }
            });
        }
    }

    /** 暂停信息流中活跃的视频（供 FeedFragment.onPause 调用）。 */
    void pauseActiveVideo() {
        if (videoController != null && playingAdId != null) {
            videoController.pause(playingAdId);
        }
    }

    /** 释放视频播放器资源（供 FeedFragment.onDestroyView 调用）。 */
    void releaseVideo() {
        if (videoController != null) {
            videoController.release();
            videoController = null;
        }
        playingAdId = null;
        playingPosition = RecyclerView.NO_POSITION;
    }

    // ---- 交互处理 ----

    /** 点击卡片：记录点击并跳转详情页。 */
    void onCardClick(AdItem ad, int position) {
        InteractionState state = interactionStore.stateOf(ad);
        state.increaseClickCount();
        analyticsTracker.trackClick(ad.getId());
        adCatalog.updateInteraction(ad.getId(), InteractionAction.CLICK);
        adapter.notifyItemChanged(position);

        context.startActivity(com.nbn.adfeed.ui.detail.AdDetailActivity.newIntent(context, ad));
        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(
                    android.R.anim.slide_in_left, android.R.anim.fade_out);
        }
    }

    /**
     * 点赞逻辑：
     * - 未点赞 → 点亮 + 弹出爱心 + 记录时间
     * - 已点赞且距上次 < 1s → 连赞，只弹出爱心不取消
     * - 已点赞且距上次 ≥ 1s → 取消点赞，不弹爱心
     */
    boolean onLikeClick(AdItem ad, int position) {
        InteractionState state = interactionStore.stateOf(ad);
        long now = SystemClock.elapsedRealtime();
        if (!state.isLiked()) {
            state.setLiked(true);
            adCatalog.updateInteraction(ad.getId(), InteractionAction.TOGGLE_LIKE);
            adapter.notifyItemChanged(position);
            lastLikeTimeMs = now;
            playLikeBurst(position);
            return true;
        }
        if (now - lastLikeTimeMs < 1000L) {
            // 1 秒内连赞：不改变状态，只弹出爱心
            adapter.notifyItemChanged(position);
            playLikeBurst(position);
            return true;
        }
        // 超过 1 秒：取消点赞
        state.setLiked(false);
        adCatalog.updateInteraction(ad.getId(), InteractionAction.TOGGLE_LIKE);
        adapter.notifyItemChanged(position);
        return false;
    }

    /** 收藏切换。 */
    void onCollectClick(AdItem ad, int position) {
        interactionStore.toggleCollect(ad);
        adCatalog.updateInteraction(ad.getId(), InteractionAction.TOGGLE_COLLECT);
        adapter.notifyItemChanged(position);
    }

    /** 分享：上报事件 + 弹出系统分享面板。 */
    void onShareClick(AdItem ad, int position) {
        adCatalog.updateInteraction(ad.getId(), InteractionAction.SHARE);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, ad.getTitle() + " · " + ad.getBrand());
        try {
            context.startActivity(Intent.createChooser(share, context.getString(R.string.feed_action_share)));
        } catch (Exception ignored) {
        }
        Toast.makeText(context,
                context.getString(R.string.feed_shared_toast, ad.getTitle()),
                Toast.LENGTH_SHORT).show();
    }

    /** 视频播放/暂停切换。使用成员C的 Media3VideoPlayerController 实现真实播放。 */
    void onVideoPlayClick(AdItem ad, int position) {
        if (videoController == null || ad == null) {
            return;
        }
        String adId = ad.getId();
        String videoUrl = ad.getVideoUrl();
        if (videoUrl == null || videoUrl.trim().isEmpty()) {
            Toast.makeText(context,
                    context.getString(R.string.detail_video_unavailable),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 找到当前卡片的 ViewHolder，获取播放器相关控件。
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (!(holder instanceof FeedAdViewHolder)) {
            return;
        }
        FeedAdViewHolder vh = (FeedAdViewHolder) holder;

        // 如果点击的是同一张正在播放的卡 → 暂停。
        if (adId.equals(playingAdId)) {
            videoController.pause(adId);
            showVideoCoverUi(vh, true);
            playingAdId = null;
            playingPosition = RecyclerView.NO_POSITION;
            return;
        }

        // 停止前一张卡的播放并恢复其封面态。
        restorePlayingCardUi();

        // 开始播放当前卡。
        PlayerView playerView = vh.videoPlayerView;
        if (playerView == null) {
            return;
        }
        boolean started = videoController.play(adId, videoUrl, playerView);
        if (started) {
            playingAdId = adId;
            playingPosition = position;
            showVideoPlayingUi(vh, false);
        } else {
            Toast.makeText(context,
                    context.getString(R.string.detail_video_unavailable),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ---- 视频 UI 切换 ----

    /** 切换卡片到"播放中"模式：隐藏封面与遮罩，显示 PlayerView。 */
    private void showVideoPlayingUi(FeedAdViewHolder vh, boolean showCover) {
        if (vh.mediaImage != null) vh.mediaImage.setVisibility(showCover ? View.VISIBLE : View.GONE);
        if (vh.videoScrim != null) vh.videoScrim.setVisibility(View.GONE);
        if (vh.playButton != null) vh.playButton.setVisibility(View.GONE);
        if (vh.videoPlayerView != null) vh.videoPlayerView.setVisibility(View.VISIBLE);
        if (vh.videoStateText != null) {
            vh.videoStateText.setText(vh.itemView.getContext().getString(R.string.detail_playing_hint));
        }
    }

    /** 切换卡片到"暂停/封面"模式：隐藏 PlayerView，显示封面与播放按钮。 */
    private void showVideoCoverUi(FeedAdViewHolder vh, boolean showCover) {
        if (vh.mediaImage != null) vh.mediaImage.setVisibility(showCover ? View.VISIBLE : View.GONE);
        if (vh.videoScrim != null) vh.videoScrim.setVisibility(View.VISIBLE);
        if (vh.playButton != null) vh.playButton.setVisibility(View.VISIBLE);
        if (vh.videoPlayerView != null) vh.videoPlayerView.setVisibility(View.GONE);
        if (vh.videoStateText != null) {
            vh.videoStateText.setText(vh.itemView.getContext().getString(R.string.detail_pause_hint));
        }
    }

    /**
     * 恢复当前播放中卡片的封面态。
     * 播放结束 / 出错 / 切换到另一张卡时调用。
     */
    private void restorePlayingCardUi() {
        if (playingAdId == null || playingPosition == RecyclerView.NO_POSITION) {
            return;
        }
        // 先暂停播放器。
        if (videoController != null) {
            videoController.releaseOffscreen(playingAdId);
        }
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(playingPosition);
        if (holder instanceof FeedAdViewHolder) {
            showVideoCoverUi((FeedAdViewHolder) holder, true);
        }
        playingAdId = null;
        playingPosition = RecyclerView.NO_POSITION;
    }

    /**
     * 抖音式连赞特效：爱心从点赞按钮位置弹出，缩放放大后向上飘动、
     * 随机旋转、逐渐放大并淡出。每次点击独立创建爱心，互不干扰。
     */
    private void playLikeBurst(int position) {
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (!(holder instanceof FeedAdViewHolder)) return;
        View likeIcon = holder.itemView.findViewById(R.id.likeIcon);
        if (likeIcon == null) return;

        View root = recyclerView.getRootView();
        if (!(root instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) root;

        float density = root.getResources().getDisplayMetrics().density;
        int size = (int) (density * 80);

        // 计算爱心起始位置（like 图标正上方）
        int[] loc = new int[2];
        likeIcon.getLocationOnScreen(loc);
        int[] rootLoc = new int[2];
        root.getLocationOnScreen(rootLoc);
        float startX = loc[0] - rootLoc[0] + likeIcon.getWidth() / 2f - size / 2f;
        float startY = loc[1] - rootLoc[1] - size / 2f;

        ImageView heart = new ImageView(root.getContext());
        heart.setImageResource(R.drawable.ic_like);
        heart.setColorFilter(root.getContext().getColor(R.color.nbn_like_active));
        heart.setAlpha(0f);
        heart.setScaleX(0.4f);
        heart.setScaleY(0.4f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.leftMargin = (int) startX;
        params.topMargin = (int) startY;
        heart.setLayoutParams(params);
        parent.addView(heart);

        // 向上飘动距离（屏幕高度的 30%~50%，随机让轨迹不同）
        float riseDistance = -(density * (180 + (float) Math.random() * 120));
        // 水平漂移（±40dp 随机，形成扇形扩散）
        float driftX = density * ((float) (Math.random() - 0.5) * 80);
        // 随机旋转角度
        float rotation = (float) ((Math.random() - 0.5) * 60);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                // 缩放：0.4 → 1.3 → 1.6
                ObjectAnimator.ofFloat(heart, "scaleX", 0.4f, 1.2f, 1.5f),
                ObjectAnimator.ofFloat(heart, "scaleY", 0.4f, 1.2f, 1.5f),
                // 透明度：渐显 → 保持 → 渐隐
                ObjectAnimator.ofFloat(heart, "alpha", 0f, 1f, 1f, 0.4f, 0f),
                // 向上飘动
                ObjectAnimator.ofFloat(heart, "translationY", 0f, riseDistance * 0.6f, riseDistance),
                // 水平漂移
                ObjectAnimator.ofFloat(heart, "translationX", 0f, driftX * 0.3f, driftX),
                // 旋转
                ObjectAnimator.ofFloat(heart, "rotation", 0f, rotation * 0.4f, rotation)
        );
        set.setDuration(1000);
        // alpha 关键帧时间分布：前 30% 渐变显，中间 40% 保持，后 30% 渐隐
        ((ObjectAnimator) set.getChildAnimations().get(2))
                .setInterpolator(new LinearInterpolator());
        set.setInterpolator(new AccelerateInterpolator(0.8f));

        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                parent.removeView(heart);
            }
        });
        set.start();
    }
}
