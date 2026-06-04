package com.nbn.adfeed.analytics.event;

/**
 * 单个广告从统计事件表汇总出的曝光/点击次数。
 */
public final class AdAnalyticsEventCounts {
    private int exposureCount;
    private int clickCount;

    public int getExposureCount() {
        return exposureCount;
    }

    public int getClickCount() {
        return clickCount;
    }

    void setExposureCount(int exposureCount) {
        this.exposureCount = exposureCount;
    }

    void setClickCount(int clickCount) {
        this.clickCount = clickCount;
    }
}
