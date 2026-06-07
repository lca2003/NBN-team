package com.nbn.adfeed.ui.feed;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nbn.adfeed.R;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionState;
import com.nbn.adfeed.ui.media.AdMediaLoader;

/**
 * 广告卡片 ViewHolder，三种样式共用（大图 / 小图 / 视频）。
 *
 * <p>三种卡片布局里 id 命名保持一致（titleText / summaryText / brandText / tagGroup /
 * statsText 以及互动栏的 likeContainer 等），因此可以用同一个 ViewHolder 绑定。
 * 差异仅在视频卡多出播放按钮、播控状态文案、遮罩层与 PlayerView，非视频卡这些字段为 null。</p>
 *
 * <p>字段为 package-visible，方便 {@link FeedInteractionDelegate} 在视频播放时直接操作
 * 封面图、遮罩、播放按钮和 PlayerView 的可见性。</p>
 */
final class FeedAdViewHolder extends RecyclerView.ViewHolder {

    final ImageView mediaImage;
    final TextView brandText;
    final TextView titleText;
    final TextView summaryText;
    final LinearLayout tagGroup;
    final TextView statsText;

    // 互动栏（来自 include 的 view_interaction_bar）。
    final View likeContainer;
    final ImageView likeIcon;
    final View collectContainer;
    final ImageView collectIcon;
    final View shareContainer;

    // 仅视频卡有这些控件；其他卡为 null。
    final View playButton;
    final TextView videoStateText;
    final View videoScrim;
    final androidx.media3.ui.PlayerView videoPlayerView;

    /**
     * @param itemView 卡片根布局
     * @param isVideo  是否为视频卡（决定是否查找视频专属控件）
     */
    FeedAdViewHolder(@NonNull View itemView, boolean isVideo) {
        super(itemView);
        mediaImage = itemView.findViewById(R.id.mediaImage);
        brandText = itemView.findViewById(R.id.brandText);
        titleText = itemView.findViewById(R.id.titleText);
        summaryText = itemView.findViewById(R.id.summaryText);
        tagGroup = itemView.findViewById(R.id.tagGroup);
        statsText = itemView.findViewById(R.id.statsText);

        likeContainer = itemView.findViewById(R.id.likeContainer);
        likeIcon = itemView.findViewById(R.id.likeIcon);
        collectContainer = itemView.findViewById(R.id.collectContainer);
        collectIcon = itemView.findViewById(R.id.collectIcon);
        shareContainer = itemView.findViewById(R.id.shareContainer);

        playButton = isVideo ? itemView.findViewById(R.id.playButton) : null;
        videoStateText = isVideo ? itemView.findViewById(R.id.videoStateText) : null;
        videoScrim = isVideo ? itemView.findViewById(R.id.videoScrim) : null;
        videoPlayerView = isVideo ? itemView.findViewById(R.id.videoPlayerView) : null;
    }

    /**
     * 完整绑定一张广告卡片：图片、文字、标签 chip、互动状态、点击监听。
     *
     * @param ad               广告数据
     * @param listener         交互回调（点击/点赞/收藏/分享/标签/视频播放）
     * @param selectedTags     当前筛选选中的标签集合（命中标签高亮）
     * @param interactionStore 互动状态单一数据源
     */
    void bind(AdItem ad,
              FeedInteractionListener listener,
              java.util.Set<String> selectedTags,
              InteractionStore interactionStore) {
        // 使用 AdMediaLoader 加载广告图片（https 优先，失败走 fallback）。
        AdMediaLoader.loadFeedImage(mediaImage, ad);
        brandText.setText(ad.getBrand());
        titleText.setText(ad.getTitle());
        summaryText.setText(ad.getSummary());

        // 标签 chip 渲染，命中筛选的标签高亮。
        TagChipBinder.bind(tagGroup, ad.getTags(), tag -> {
            int pos = getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onTagClick(ad, tag, pos);
            }
        }, selectedTags);

        // 互动状态统一从 Store 读取，保证与详情页一致。
        InteractionState state = interactionStore.stateOf(ad);
        renderLike(state.isLiked());
        renderCollect(state.isCollected());
        statsText.setText(itemView.getContext().getString(
                R.string.feed_stats_format, state.getExposureCount(), state.getClickCount()));

        // 视频卡默认暂停态文案。
        if (videoStateText != null) {
            videoStateText.setText(itemView.getContext().getString(R.string.detail_pause_hint));
        }

        // 整张卡片点击进详情。
        itemView.setOnClickListener(v -> {
            int pos = getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onCardClick(ad, pos);
            }
        });

        likeContainer.setOnClickListener(v -> {
            int pos = getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onLikeClick(ad, pos);
            }
        });
        collectContainer.setOnClickListener(v -> {
            int pos = getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onCollectClick(ad, pos);
            }
        });
        shareContainer.setOnClickListener(v -> {
            int pos = getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onShareClick(ad, pos);
            }
        });
        if (playButton != null) {
            playButton.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onVideoPlayClick(ad, pos);
                }
            });
        }
    }

    /**
     * 仅刷新标签 chip（payload 精简绑定），跳过图片加载、文字设置与互动栏重新绑定。
     */
    void bindTags(AdItem ad,
                  FeedInteractionListener listener,
                  java.util.Set<String> selectedTags) {
        TagChipBinder.bind(tagGroup, ad.getTags(), tag -> {
            int pos = getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onTagClick(ad, tag, pos);
            }
        }, selectedTags);
    }

    // ---- 内部渲染 ----

    /** 根据点赞态切换心形图标颜色。 */
    private void renderLike(boolean liked) {
        int color = liked
                ? itemView.getContext().getColor(R.color.nbn_like_active)
                : itemView.getContext().getColor(R.color.nbn_text_secondary);
        likeIcon.setColorFilter(color);
    }

    /** 根据收藏态切换星形图标颜色。 */
    private void renderCollect(boolean collected) {
        int color = collected
                ? itemView.getContext().getColor(R.color.nbn_collect_active)
                : itemView.getContext().getColor(R.color.nbn_text_secondary);
        collectIcon.setColorFilter(color);
    }
}
