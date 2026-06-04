package com.nbn.adfeed.data.model;

public final class InteractionState {
    private boolean liked;
    private boolean collected;
    private int exposureCount;
    private int clickCount;

    public InteractionState() {
        this(false, false, 0, 0);
    }

    public InteractionState(boolean liked, boolean collected) {
        this(liked, collected, 0, 0);
    }

    private InteractionState(boolean liked, boolean collected, int exposureCount, int clickCount) {
        this.liked = liked;
        this.collected = collected;
        this.exposureCount = exposureCount;
        this.clickCount = clickCount;
    }

    public static InteractionState empty() {
        return new InteractionState(false, false);
    }

    public boolean isLiked() {
        return liked;
    }

    public void setLiked(boolean liked) {
        this.liked = liked;
    }

    public boolean isCollected() {
        return collected;
    }

    public void setCollected(boolean collected) {
        this.collected = collected;
    }

    public int getExposureCount() {
        return exposureCount;
    }

    public void setExposureCount(int exposureCount) {
        this.exposureCount = Math.max(0, exposureCount);
    }

    public void increaseExposureCount() {
        exposureCount += 1;
    }

    public int getClickCount() {
        return clickCount;
    }

    public void setClickCount(int clickCount) {
        this.clickCount = Math.max(0, clickCount);
    }

    public void increaseClickCount() {
        clickCount += 1;
    }

    public InteractionState withLiked(boolean nextLiked) {
        return new InteractionState(nextLiked, collected, exposureCount, clickCount);
    }

    public InteractionState withCollected(boolean nextCollected) {
        return new InteractionState(liked, nextCollected, exposureCount, clickCount);
    }
}
