package com.nbn.adfeed.data.model;

public final class AdStats {
    private final int exposureCount;
    private final int clickCount;
    private final int likeCount;
    private final int collectCount;
    private final int shareCount;

    public AdStats(int exposureCount, int clickCount, int likeCount, int collectCount, int shareCount) {
        this.exposureCount = nonNegative(exposureCount);
        this.clickCount = nonNegative(clickCount);
        this.likeCount = nonNegative(likeCount);
        this.collectCount = nonNegative(collectCount);
        this.shareCount = nonNegative(shareCount);
    }

    public static AdStats empty() {
        return new AdStats(0, 0, 0, 0, 0);
    }

    public int getExposureCount() {
        return exposureCount;
    }

    public int getClickCount() {
        return clickCount;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getCollectCount() {
        return collectCount;
    }

    public int getShareCount() {
        return shareCount;
    }

    public AdStats increaseExposure() {
        return new AdStats(exposureCount + 1, clickCount, likeCount, collectCount, shareCount);
    }

    public AdStats increaseClick() {
        return new AdStats(exposureCount, clickCount + 1, likeCount, collectCount, shareCount);
    }

    public AdStats increaseLike() {
        return new AdStats(exposureCount, clickCount, likeCount + 1, collectCount, shareCount);
    }

    public AdStats decreaseLike() {
        return new AdStats(exposureCount, clickCount, Math.max(0, likeCount - 1), collectCount, shareCount);
    }

    public AdStats increaseCollect() {
        return new AdStats(exposureCount, clickCount, likeCount, collectCount + 1, shareCount);
    }

    public AdStats decreaseCollect() {
        return new AdStats(exposureCount, clickCount, likeCount, Math.max(0, collectCount - 1), shareCount);
    }

    public AdStats increaseShare() {
        return new AdStats(exposureCount, clickCount, likeCount, collectCount, shareCount + 1);
    }

    private static int nonNegative(int value) {
        return Math.max(0, value);
    }
}
