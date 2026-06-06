package com.nbn.adfeed.ui.feed;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nbn.adfeed.R;
import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionAction;
import com.nbn.adfeed.data.model.InteractionState;
import com.nbn.adfeed.ui.media.AdMediaResources;
import com.nbn.adfeed.video.VideoPlaybackManager;
import com.nbn.adfeed.video.player.Media3VideoPlayerController;

import java.util.LinkedHashMap;
import java.util.Map;

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
                public void onBuffering(String adId) {
                    showActiveVideoPreparingUi(adId);
                }

                @Override
                public void onPlaying(String adId) {
                    showActiveVideoPlayingUi(adId);
                }

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
        restorePlayingCardUi();
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
        if (liked) {
            analyticsTracker.trackLike(ad.getId());
        } else {
            analyticsTracker.trackUnlike(ad.getId());
        }
        adapter.notifyItemChanged(position);
        if (liked) {
            playLikeBurst(position);
        }
        return liked;
    }

    /** 收藏切换。 */
    void onCollectClick(AdItem ad, int position) {
        boolean collected = interactionStore.toggleCollect(ad);
        adCatalog.updateInteraction(ad.getId(), InteractionAction.TOGGLE_COLLECT);
        if (collected) {
            analyticsTracker.trackCollect(ad.getId());
        } else {
            analyticsTracker.trackUncollect(ad.getId());
        }
        adapter.notifyItemChanged(position);
    }

    /** 分享：上报事件 + 弹出系统分享面板。 */
    void onShareClick(AdItem ad, int position) {
        adCatalog.updateInteraction(ad.getId(), InteractionAction.SHARE);
        analyticsTracker.trackShare(ad.getId());
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
        String playableUri = AdMediaResources.playableVideoUri(ad);
        if (playableUri == null) {
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
            showVideoCoverUi(vh);
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
        playingAdId = adId;
        playingPosition = position;
        showVideoPreparingUi(vh);
        boolean started = videoController.play(adId, playableUri, playerView);
        if (!started) {
            showVideoCoverUi(vh);
            playingAdId = null;
            playingPosition = RecyclerView.NO_POSITION;
            Toast.makeText(context,
                    context.getString(R.string.detail_video_unavailable),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** 视频卡离屏或被 RecyclerView 复用时释放播放器绑定，避免后台播放和串卡。 */
    void onVideoCardDetached(AdItem ad) {
        if (ad == null || videoController == null) {
            return;
        }
        String adId = ad.getId();
        if (adId == null || !adId.equals(playingAdId)) {
            return;
        }
        videoController.releaseOffscreen(adId);
        playingAdId = null;
        playingPosition = RecyclerView.NO_POSITION;
    }

    /** 自动播放当前最可见的视频；切换或离屏时复用同一播放器并释放旧卡片。 */
    void autoPlayMostVisibleVideo(LinearLayoutManager layoutManager) {
        if (videoController == null || recyclerView == null || adapter == null || layoutManager == null) {
            return;
        }

        Map<Integer, Float> visibleVideoRatios = new LinkedHashMap<>();
        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
            for (int position = firstVisible; position <= lastVisible; position++) {
                AdItem ad = adapter.getAdAt(position);
                if (!isPlayableVideo(ad)) {
                    continue;
                }
                RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
                if (holder instanceof FeedAdapter.AdViewHolder) {
                    FeedAdapter.AdViewHolder videoHolder = (FeedAdapter.AdViewHolder) holder;
                    visibleVideoRatios.put(position, visibleRatioOf(videoHolder.mediaImage));
                }
            }
        }

        int selectedPosition = FeedVideoAutoPlaySelector.selectPosition(visibleVideoRatios);
        if (selectedPosition == RecyclerView.NO_POSITION) {
            restorePlayingCardUi();
            return;
        }

        AdItem selectedAd = adapter.getAdAt(selectedPosition);
        if (selectedAd != null
                && selectedAd.getId().equals(playingAdId)
                && selectedPosition == playingPosition) {
            return;
        }
        onVideoPlayClick(selectedAd, selectedPosition);
    }

    // ---- 视频 UI 切换 ----

    /** 播放请求已发出但首帧尚未就绪：保留封面，避免显示黑屏。 */
    private void showVideoPreparingUi(FeedAdapter.AdViewHolder vh) {
        FeedVideoCardUi.showPreparing(
                vh.mediaImage,
                vh.videoScrim,
                vh.playButton,
                vh.videoStateText,
                vh.videoPlayerView
        );
    }

    /** 播放器已进入真实播放态：隐藏封面并显示 PlayerView。 */
    private void showVideoPlayingUi(FeedAdapter.AdViewHolder vh) {
        FeedVideoCardUi.showPlaying(
                vh.mediaImage,
                vh.videoScrim,
                vh.playButton,
                vh.videoStateText,
                vh.videoPlayerView
        );
    }

    /** 切换卡片到"暂停/封面"模式：隐藏 PlayerView，显示封面与播放按钮。 */
    private void showVideoCoverUi(FeedAdapter.AdViewHolder vh) {
        FeedVideoCardUi.showCover(
                vh.mediaImage,
                vh.videoScrim,
                vh.playButton,
                vh.videoStateText,
                vh.videoPlayerView
        );
    }

    private void showActiveVideoPreparingUi(String adId) {
        FeedAdapter.AdViewHolder holder = activeVideoHolder(adId);
        if (holder != null) {
            showVideoPreparingUi(holder);
        }
    }

    private void showActiveVideoPlayingUi(String adId) {
        FeedAdapter.AdViewHolder holder = activeVideoHolder(adId);
        if (holder != null) {
            showVideoPlayingUi(holder);
        }
    }

    private FeedAdapter.AdViewHolder activeVideoHolder(String adId) {
        if (adId == null
                || !adId.equals(playingAdId)
                || playingPosition == RecyclerView.NO_POSITION
                || recyclerView == null) {
            return null;
        }
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(playingPosition);
        return holder instanceof FeedAdapter.AdViewHolder
                ? (FeedAdapter.AdViewHolder) holder
                : null;
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
            showVideoCoverUi((FeedAdapter.AdViewHolder) holder);
        }
        playingAdId = null;
        playingPosition = RecyclerView.NO_POSITION;
    }

    // ---- 内部方法 ----

    private boolean isPlayableVideo(AdItem ad) {
        return ad != null
                && ad.getContentType() == AdContentType.VIDEO
                && AdMediaResources.playableVideoUri(ad) != null;
    }

    private float visibleRatioOf(View videoFrame) {
        if (videoFrame == null) {
            return 0f;
        }
        int width = videoFrame.getWidth();
        int height = videoFrame.getHeight();
        if (width <= 0 || height <= 0) {
            return 0f;
        }
        Rect visibleRect = new Rect();
        if (!videoFrame.getLocalVisibleRect(visibleRect)) {
            return 0f;
        }
        return Math.min(1f, (float) (visibleRect.width() * visibleRect.height()) / (float) (width * height));
    }

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
