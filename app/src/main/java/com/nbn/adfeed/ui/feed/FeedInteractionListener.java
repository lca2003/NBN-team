package com.nbn.adfeed.ui.feed;

import com.nbn.adfeed.data.model.AdItem;

/**
 * Feed 列表的交互回调接口。
 *
 * <p>Adapter/ViewHolder 只负责“把事件抛出来”，具体怎么处理（更新状态、上报统计、跳详情、
 * 播放视频）由持有列表的 {@link FeedFragment} 决定。这样 Adapter 不直接依赖统计、视频等其他
 * 同学的模块，职责清晰、便于测试。</p>
 */
public interface FeedInteractionListener {

    /** 点击整张卡片，进入详情页。position 用于返回后定位列表位置。 */
    void onCardClick(AdItem ad, int position);

    /** 点击点赞。返回值由调用方决定，这里只负责通知。 */
    void onLikeClick(AdItem ad, int position);

    /** 点击收藏。 */
    void onCollectClick(AdItem ad, int position);

    /** 点击分享。 */
    void onShareClick(AdItem ad, int position);
    // 点击标签
    void onTagClick(AdItem ad, String tag, int position);

    /** 点击视频卡的播放按钮（仅视频卡触发）。 */
    void onVideoPlayClick(AdItem ad, int position);
}
