package com.nbn.adfeed.ui.feed;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.nbn.adfeed.R;
import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 单列信息流适配器，负责数据管理与 ViewType 分发。
 *
 * <p>卡片绑定已委托给 {@link FeedAdViewHolder}，footer 绑定已委托给
 * {@link FeedFooterViewHolder}，本类仅保留数据增删改与 RecyclerView 标准回调。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>按 {@link AdContentType} 返回不同 viewType，三种卡片共享一个 ViewHolder。</li>
 *   <li>末尾追加一个 footer item 展示"加载中 / 没有更多 / 重试"。</li>
 *   <li>标签筛选变化时通过 payload "tags" 做精简绑定（只刷新 chip，不重新加载图片）。</li>
 * </ul>
 */
public final class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // 卡片 viewType。
    private static final int TYPE_LARGE_IMAGE = 1;
    private static final int TYPE_SMALL_IMAGE = 2;
    private static final int TYPE_VIDEO = 3;
    private static final int TYPE_FOOTER = 9;

    /** Payload key：标签筛选变化时只刷新 tag chip。 */
    private static final String PAYLOAD_TAGS = "tags";

    private final List<AdItem> items = new ArrayList<>();
    private final FeedInteractionListener listener;
    private final InteractionStore interactionStore = InteractionStore.get();

    /** 当前筛选选中的标签集合，命中的标签会在卡片上高亮展示。 */
    private java.util.Set<String> selectedTags = java.util.Collections.emptySet();

    /** 当前 footer 状态。 */
    private FooterState footerState = FooterState.HIDDEN;

    /** footer 错误态的重试回调，由 Fragment 注入。 */
    private Runnable footerRetryListener;

    public FeedAdapter(FeedInteractionListener listener) {
        this.listener = listener;
        setHasStableIds(false);
    }

    // ---- 数据操作 ----

    /** 更新筛选标签集合并通过 payload 刷新（只重建 chip，不重新加载图片/文字）。 */
    public void setSelectedTags(java.util.Set<String> tags) {
        this.selectedTags = (tags == null) ? java.util.Collections.emptySet() : new java.util.HashSet<>(tags);
        notifyItemRangeChanged(0, items.size(), PAYLOAD_TAGS);
    }

    /** 整体替换数据（下拉刷新、切换频道时用）。 */
    public void submit(List<AdItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    /** 追加一页数据（上拉加载更多时用）。 */
    public void append(List<AdItem> moreItems) {
        if (moreItems == null || moreItems.isEmpty()) return;
        int start = items.size();
        items.addAll(moreItems);
        notifyItemRangeInserted(start, moreItems.size());
    }

    /** 更新 footer 状态并刷新最后一项。 */
    public void setFooterState(FooterState state) {
        if (footerState == state) return;
        footerState = state;
        notifyItemChanged(items.size());
    }

    public void setFooterRetryListener(Runnable retry) {
        this.footerRetryListener = retry;
    }

    public FooterState getFooterState() { return footerState; }
    public boolean isEmpty() { return items.isEmpty(); }

    /** 根据 RecyclerView 位置获取广告数据。 */
    @Nullable
    public AdItem getAdAt(int position) {
        if (position < 0 || position >= items.size()) return null;
        return items.get(position);
    }

    // ---- RecyclerView.Adapter 回调 ----

    @Override
    public int getItemCount() {
        return items.size() + 1; // 数据条数 + 1 个 footer
    }

    @Override
    public int getItemViewType(int position) {
        if (position == items.size()) return TYPE_FOOTER;
        AdContentType type = items.get(position).getContentType();
        switch (type) {
            case VIDEO:       return TYPE_VIDEO;
            case SMALL_IMAGE: return TYPE_SMALL_IMAGE;
            default:          return TYPE_LARGE_IMAGE;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_VIDEO:
                return new FeedAdViewHolder(
                        inflater.inflate(R.layout.item_ad_video, parent, false), true);
            case TYPE_SMALL_IMAGE:
                return new FeedAdViewHolder(
                        inflater.inflate(R.layout.item_ad_small_image, parent, false), false);
            case TYPE_FOOTER:
                return new FeedFooterViewHolder(
                        inflater.inflate(R.layout.item_feed_footer, parent, false));
            default:
                return new FeedAdViewHolder(
                        inflater.inflate(R.layout.item_ad_large_image, parent, false), false);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FeedFooterViewHolder) {
            ((FeedFooterViewHolder) holder).bind(footerState, footerRetryListener);
            return;
        }
        AdItem ad = items.get(position);
        ((FeedAdViewHolder) holder).bind(ad, listener, selectedTags, interactionStore);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        if (holder instanceof FeedAdViewHolder) {
            AdItem ad = items.get(position);
            for (Object payload : payloads) {
                if (PAYLOAD_TAGS.equals(payload)) {
                    ((FeedAdViewHolder) holder).bindTags(ad, listener, selectedTags);
                    return;
                }
            }
        }
        onBindViewHolder(holder, position);
    }
}
