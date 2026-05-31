package com.nbn.adfeed.data.model;

public final class InteractionState {
    private final boolean liked;
    private final boolean collected;

    public InteractionState() {
        this(false, false);
    }

    public InteractionState(boolean liked, boolean collected) {
        this.liked = liked;
        this.collected = collected;
    }

    public static InteractionState empty() {
        return new InteractionState(false, false);
    }

    public boolean isLiked() {
        return liked;
    }

    public boolean isCollected() {
        return collected;
    }

    public InteractionState withLiked(boolean nextLiked) {
        return new InteractionState(nextLiked, collected);
    }

    public InteractionState withCollected(boolean nextCollected) {
        return new InteractionState(liked, nextCollected);
    }
}
