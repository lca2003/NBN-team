package com.nbn.adfeed.analytics.event;

/**
 * 单个广告从统计事件表汇总出的曝光、点击和互动事件计数。
 */
public final class AdAnalyticsEventCounts {
    private int exposureCount;
    private int clickCount;
    private int likeDelta;
    private int collectDelta;
    private int shareCount;

    public AdAnalyticsEventCounts() {
    }

    public AdAnalyticsEventCounts(
            int exposureCount,
            int clickCount,
            int likeDelta,
            int collectDelta,
            int shareCount
    ) {
        this.exposureCount = Math.max(0, exposureCount);
        this.clickCount = Math.max(0, clickCount);
        this.likeDelta = likeDelta;
        this.collectDelta = collectDelta;
        this.shareCount = Math.max(0, shareCount);
    }

    public int getExposureCount() {
        return exposureCount;
    }

    public int getClickCount() {
        return clickCount;
    }

    public int getLikeDelta() {
        return likeDelta;
    }

    public int getCollectDelta() {
        return collectDelta;
    }

    public int getShareCount() {
        return shareCount;
    }

    void setExposureCount(int exposureCount) {
        this.exposureCount = Math.max(0, exposureCount);
    }

    void setClickCount(int clickCount) {
        this.clickCount = Math.max(0, clickCount);
    }

    void increaseLikeDelta() {
        likeDelta += 1;
    }

    void decreaseLikeDelta() {
        likeDelta -= 1;
    }

    void increaseCollectDelta() {
        collectDelta += 1;
    }

    void decreaseCollectDelta() {
        collectDelta -= 1;
    }

    void setShareCount(int shareCount) {
        this.shareCount = Math.max(0, shareCount);
    }
}
