package com.nbn.adfeed.ui.feed;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
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
                }

                @Override
                public void onError(String adId, String message) {
                    restorePlayingCardUi();
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

    /** 点赞切换，返回是否为"点亮"状态（用于外层决定是否播放彩蛋）。 */
    boolean onLikeClick(AdItem ad, int position) {
        boolean liked = interactionStore.toggleLike(ad);
        adCatalog.updateInteraction(ad.getId(), InteractionAction.TOGGLE_LIKE);
        adapter.notifyItemChanged(position);
        if (liked) {
            playLikeBurst(position);
        }
        return liked;
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
        if (!(holder instanceof FeedAdapter.AdViewHolder)) {
            return;
        }
        FeedAdapter.AdViewHolder vh = (FeedAdapter.AdViewHolder) holder;

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
    private void showVideoPlayingUi(FeedAdapter.AdViewHolder vh, boolean showCover) {
        if (vh.mediaImage != null) vh.mediaImage.setVisibility(showCover ? View.VISIBLE : View.GONE);
        if (vh.videoScrim != null) vh.videoScrim.setVisibility(View.GONE);
        if (vh.playButton != null) vh.playButton.setVisibility(View.GONE);
        if (vh.videoPlayerView != null) vh.videoPlayerView.setVisibility(View.VISIBLE);
    }

    /** 切换卡片到"暂停/封面"模式：隐藏 PlayerView，显示封面与播放按钮。 */
    private void showVideoCoverUi(FeedAdapter.AdViewHolder vh, boolean showCover) {
        if (vh.mediaImage != null) vh.mediaImage.setVisibility(showCover ? View.VISIBLE : View.GONE);
        if (vh.videoScrim != null) vh.videoScrim.setVisibility(View.VISIBLE);
        if (vh.playButton != null) vh.playButton.setVisibility(View.VISIBLE);
        if (vh.videoPlayerView != null) vh.videoPlayerView.setVisibility(View.GONE);
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
        if (holder instanceof FeedAdapter.AdViewHolder) {
            showVideoCoverUi((FeedAdapter.AdViewHolder) holder, true);
        }
        playingAdId = null;
        playingPosition = RecyclerView.NO_POSITION;
    }

    // ---- 内部方法 ----

    /**
     * 点赞彩蛋动画：心形图标放大回弹。
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
        likeIcon.animate()
                .scaleX(1.25f).scaleY(1.25f)
                .setDuration(160)
                .setInterpolator(new OvershootInterpolator())
                .withEndAction(() -> likeIcon.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(120)
                        .start())
                .start();
    }
}
