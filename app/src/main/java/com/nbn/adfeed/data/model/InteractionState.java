package com.nbn.adfeed.data.model;

public final class InteractionState {
    private boolean liked;
    private boolean collected;
    private int exposureCount;
    private int clickCount;

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

    public void increaseExposureCount() {
        exposureCount += 1;
    }

    public int getClickCount() {
        return clickCount;
    }

    public void increaseClickCount() {
        clickCount += 1;
    }
}
