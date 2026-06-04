package com.nbn.adfeed.ui.feed;

import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionState;

import java.util.HashMap;
import java.util.Map;

/**
 * 互动状态的“单一数据源”（UI 层）。
 *
 * <p>课题要求：详情页和信息流的点赞/收藏状态要保持同步。为避免每个页面各自复制一份
 * 孤立的状态（点了详情页的赞，回到列表却没变），这里用一个进程内单例按 adId 统一保存
 * 点赞、收藏、分享、曝光、点击等互动状态。Feed 列表和详情页都读写同一份 {@link InteractionState}。</p>
 *
 * <p>边界说明：本类属于人员A的 {@code ui.feed} 包，只复用人员B定义的 {@link InteractionState}
 * 模型，不修改数据层文件。后续若接入真正的网络持久化，可在这里替换为带远端同步的实现。</p>
 */
public final class InteractionStore {

    /** 进程内单例：保证 Feed 与 Detail 拿到的是同一份状态。 */
    private static final InteractionStore INSTANCE = new InteractionStore();

    /** adId -> 该广告的互动状态。 */
    private final Map<String, InteractionState> states = new HashMap<>();

    private InteractionStore() {
    }

    public static InteractionStore get() {
        return INSTANCE;
    }

    /**
     * 取出（必要时创建）指定广告的互动状态。
     *
     * <p>首次遇到某条广告时，用广告自带的 {@link AdItem#getInteractionState()} 作为初始值，
     * 之后所有页面都以 Store 里的这一份为准。</p>
     */
    public InteractionState stateOf(AdItem ad) {
        InteractionState existing = states.get(ad.getId());
        if (existing != null) {
            return existing;
        }
        // 用广告初始携带的状态做种子，保证 Mock 数据里预置的点赞/收藏不丢失。
        InteractionState seed = ad.getInteractionState();
        InteractionState state = seed != null ? seed : new InteractionState();
        states.put(ad.getId(), state);
        return state;
    }

    /** 按 adId 直接取状态，没有则返回 null（详情页通过 id 回查时使用）。 */
    public InteractionState stateOf(String adId) {
        return states.get(adId);
    }

    /** 切换点赞状态，返回切换后的结果，方便调用方决定是否播放彩蛋动画。 */
    public boolean toggleLike(AdItem ad) {
        InteractionState state = stateOf(ad);
        state.setLiked(!state.isLiked());
        return state.isLiked();
    }

    /** 切换收藏状态，返回切换后的结果。 */
    public boolean toggleCollect(AdItem ad) {
        InteractionState state = stateOf(ad);
        state.setCollected(!state.isCollected());
        return state.isCollected();
    }

    /** 用 SQLite 汇总结果覆盖曝光/点击计数，点赞/收藏状态保持不变。 */
    public void applyCounts(AdItem ad, int exposureCount, int clickCount) {
        InteractionState state = stateOf(ad);
        state.setExposureCount(exposureCount);
        state.setClickCount(clickCount);
    }

}
