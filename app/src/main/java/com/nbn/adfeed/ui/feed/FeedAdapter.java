package com.nbn.adfeed.ui.feed;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.nbn.adfeed.R;
import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionState;

import java.util.ArrayList;
import java.util.List;

/**
 * 单列信息流适配器，支持多样式卡片（大图/小图/视频）+ 底部加载 footer。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>按 {@link AdContentType} 返回不同 viewType，复用对应 ViewHolder，满足课题“多样式卡片 + cell 复用”。</li>
 *   <li>互动状态统一从 {@link InteractionStore} 读取，保证与详情页同步。</li>
 *   <li>所有点击事件通过 {@link FeedInteractionListener} 抛给 Fragment，Adapter 不直接耦合统计/视频模块。</li>
 *   <li>列表末尾追加一个 footer item 展示“加载中/没有更多/重试”。</li>
 * </ul>
 */
public final class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // 卡片 viewType。
    private static final int TYPE_LARGE_IMAGE = 1;
    private static final int TYPE_SMALL_IMAGE = 2;
    private static final int TYPE_VIDEO = 3;
    private static final int TYPE_FOOTER = 9;

    private final List<AdItem> items = new ArrayList<>();
    private final FeedInteractionListener listener;
    private final InteractionStore interactionStore = InteractionStore.get();

    /** 当前 footer 状态，决定列表末尾那一项怎么显示。 */
    private FooterState footerState = FooterState.HIDDEN;

    public FeedAdapter(FeedInteractionListener listener) {
        this.listener = listener;
        // 稳定 id 有助于刷新时的动画与复用正确性。
        setHasStableIds(false);
    }

    /** 整体替换数据（下拉刷新、切换频道时用）。 */
    public void submit(List<AdItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    /** 追加一页数据（上拉加载更多时用）。 */
    public void append(List<AdItem> moreItems) {
        if (moreItems == null || moreItems.isEmpty()) {
            return;
        }
        int start = items.size();
        items.addAll(moreItems);
        notifyItemRangeInserted(start, moreItems.size());
    }

    /** 更新 footer 状态并刷新最后一项。 */
    public void setFooterState(FooterState state) {
        if (footerState == state) {
            return;
        }
        footerState = state;
        notifyItemChanged(items.size());
    }

    public FooterState getFooterState() {
        return footerState;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    //让 FeedFragment 能根据 RecyclerView 位置拿到对应广告数据
    @Nullable
    public AdItem getAdAt(int position) {
        if (position < 0 || position >= items.size()) {
            return null;
        }
        return items.get(position);
    }

    /** 列表项数 = 数据条数 + 1 个 footer。 */
    @Override
    public int getItemCount() {
        return items.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == items.size()) {
            return TYPE_FOOTER;
        }
        AdContentType type = items.get(position).getContentType();
        switch (type) {
            case VIDEO:
                return TYPE_VIDEO;
            case SMALL_IMAGE:
                return TYPE_SMALL_IMAGE;
            case LARGE_IMAGE:
            default:
                return TYPE_LARGE_IMAGE;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_VIDEO:
                return new AdViewHolder(
                        inflater.inflate(R.layout.item_ad_video, parent, false), true);
            case TYPE_SMALL_IMAGE:
                return new AdViewHolder(
                        inflater.inflate(R.layout.item_ad_small_image, parent, false), false);
            case TYPE_FOOTER:
                return new FooterViewHolder(
                        inflater.inflate(R.layout.item_feed_footer, parent, false));
            case TYPE_LARGE_IMAGE:
            default:
                return new AdViewHolder(
                        inflater.inflate(R.layout.item_ad_large_image, parent, false), false);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FooterViewHolder) {
            ((FooterViewHolder) holder).bind(footerState);
            return;
        }
        AdItem ad = items.get(position);
        ((AdViewHolder) holder).bind(ad);
    }

    /**
     * 广告卡片 ViewHolder，三种样式共用。
     *
     * <p>三种卡片布局里 id 命名保持一致（titleText/summaryText/brandText/tagGroup/statsText
     * 以及互动栏的 likeContainer 等），因此可以用同一个 ViewHolder 绑定，差异只在视频卡多出
     * 播放按钮与播放状态。</p>
     */
    final class AdViewHolder extends RecyclerView.ViewHolder {
        private final TextView brandText;
        private final TextView titleText;
        private final TextView summaryText;
        private final LinearLayout tagGroup;
        private final TextView statsText;

        // 互动栏（来自 include 的 view_interaction_bar）。
        private final View likeContainer;
        private final ImageView likeIcon;
        private final View collectContainer;
        private final ImageView collectIcon;
        private final View shareContainer;

        // 仅视频卡有这两个；其他卡为 null。
        private final View playButton;
        private final TextView videoStateText;

        AdViewHolder(@NonNull View itemView, boolean isVideo) {
            super(itemView);
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
        }

        void bind(AdItem ad) {
            brandText.setText(ad.getBrand());
            titleText.setText(ad.getTitle());
            summaryText.setText(ad.getSummary());
            TagChipBinder.bind(tagGroup, ad.getTags());

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

            // 整张卡片点击进详情。getBindingAdapterPosition 避免复用后位置错乱。
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

        /** 根据点赞态切换心形图标颜色。 */
        private void renderLike(boolean liked) {
            int color = liked
                    ? itemView.getContext().getColor(R.color.nbn_like_active)
                    : itemView.getContext().getColor(R.color.nbn_text_secondary);
            likeIcon.setColorFilter(color);
        }

        /** 根据收藏态切换图标颜色。 */
        private void renderCollect(boolean collected) {
            int color = collected
                    ? itemView.getContext().getColor(R.color.nbn_collect_active)
                    : itemView.getContext().getColor(R.color.nbn_text_secondary);
            collectIcon.setColorFilter(color);
        }
    }

    /** 底部 footer 的 ViewHolder：三态切换 + 错误态点击重试。 */
    final class FooterViewHolder extends RecyclerView.ViewHolder {
        private final View loadingView;
        private final TextView textView;

        FooterViewHolder(@NonNull View itemView) {
            super(itemView);
            loadingView = itemView.findViewById(R.id.footerLoading);
            textView = itemView.findViewById(R.id.footerText);
        }

        void bind(FooterState state) {
            switch (state) {
                case LOADING:
                    loadingView.setVisibility(View.VISIBLE);
                    textView.setVisibility(View.GONE);
                    itemView.setOnClickListener(null);
                    break;
                case NO_MORE:
                    loadingView.setVisibility(View.GONE);
                    textView.setVisibility(View.VISIBLE);
                    textView.setText(R.string.feed_no_more);
                    itemView.setOnClickListener(null);
                    break;
                case ERROR:
                    loadingView.setVisibility(View.GONE);
                    textView.setVisibility(View.VISIBLE);
                    textView.setText(R.string.feed_error);
                    // 错误态：点击 footer 触发重试。复用 onVideoPlayClick 之外的语义不合适，
                    // 这里用专门的重试回调更清晰 —— 通过 listener 之外的字段暴露。
                    itemView.setOnClickListener(v -> {
                        if (footerRetryListener != null) {
                            footerRetryListener.run();
                        }
                    });
                    break;
                case HIDDEN:
                default:
                    loadingView.setVisibility(View.GONE);
                    textView.setVisibility(View.GONE);
                    itemView.setOnClickListener(null);
                    break;
            }
        }
    }

    /** footer 错误态的重试回调，由 Fragment 注入。 */
    private Runnable footerRetryListener;

    public void setFooterRetryListener(Runnable retry) {
        this.footerRetryListener = retry;
    }
}
