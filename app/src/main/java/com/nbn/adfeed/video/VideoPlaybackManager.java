package com.nbn.adfeed.video;

public final class VideoPlaybackManager {
    private String activeAdId;
    private boolean muted = true;

    public void bindActiveVideo(String adId) {
        activeAdId = adId;
    }

    public void pauseActiveVideo() {
        activeAdId = null;
    }

    public String getActiveAdId() {
        return activeAdId;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }
}
