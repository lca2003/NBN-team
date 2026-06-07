package com.nbn.adfeed.ui.feed;

import com.nbn.adfeed.data.model.AdItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 一页广告数据的载体（UI 层分页模型）。
 *
 * <p>对齐 {@code docs/api-contract.md} 中的“分页响应”结构（items / nextCursor / hasMore），
 * 这样后续人员B提供真实分页接口时，Feed 层的消费方式几乎不用改。</p>
 */
public final class FeedPage {

    private final List<AdItem> items;
    private final boolean hasMore;

    public FeedPage(List<AdItem> items, boolean hasMore) {
        this.items = new ArrayList<>(items);
        this.hasMore = hasMore;
    }

    public List<AdItem> getItems() {
        return items;
    }

    /** 是否还有下一页，决定 Feed 是否继续触发“上拉加载更多”。 */
    public boolean hasMore() {
        return hasMore;
    }
}