package com.nbn.adfeed.video;

import java.util.HashMap;
import java.util.Map;

public final class VideoPlaybackManager {
    public enum PlaybackState {
        IDLE,
        PLAYING,
        PAUSED
    }

    private final Map<String, PlaybackState> playbackStates = new HashMap<>();
    private final Map<String, Long> positionsMs = new HashMap<>();
    private String activeAdId;
    private boolean muted = true;

    public void play(String adId) {
        String normalizedAdId = normalizeAdId(adId);
        if (normalizedAdId == null) {
            return;
        }

        pauseCurrentIfReplacing(normalizedAdId);
        activeAdId = normalizedAdId;
        playbackStates.put(normalizedAdId, PlaybackState.PLAYING);
    }

    public void pause(String adId) {
        String normalizedAdId = normalizeAdId(adId);
        if (normalizedAdId == null) {
            return;
        }

        if (normalizedAdId.equals(activeAdId)) {
            playbackStates.put(normalizedAdId, PlaybackState.PAUSED);
        }
    }

    public void pause(String adId, long positionMs) {
        updatePositionMs(adId, positionMs);
        pause(adId);
    }

    public void resume(String adId) {
        String normalizedAdId = normalizeAdId(adId);
        if (normalizedAdId == null) {
            return;
        }

        pauseCurrentIfReplacing(normalizedAdId);
        activeAdId = normalizedAdId;
        playbackStates.put(normalizedAdId, PlaybackState.PLAYING);
    }

    public void stop(String adId) {
        String normalizedAdId = normalizeAdId(adId);
        if (normalizedAdId == null) {
            return;
        }

        playbackStates.put(normalizedAdId, PlaybackState.IDLE);
        clearActiveIfMatches(normalizedAdId);
    }

    public void stop(String adId, long positionMs) {
        updatePositionMs(adId, positionMs);
        stop(adId);
    }

    public void releaseOffscreen(String adId) {
        String normalizedAdId = normalizeAdId(adId);
        if (normalizedAdId == null) {
            return;
        }

        playbackStates.put(normalizedAdId, PlaybackState.IDLE);
        clearActiveIfMatches(normalizedAdId);
    }

    public void releaseOffscreen(String adId, long positionMs) {
        updatePositionMs(adId, positionMs);
        releaseOffscreen(adId);
    }

    public void releaseAll() {
        for (String adId : playbackStates.keySet()) {
            playbackStates.put(adId, PlaybackState.IDLE);
        }
        activeAdId = null;
    }

    public void updatePositionMs(String adId, long positionMs) {
        String normalizedAdId = normalizeAdId(adId);
        if (normalizedAdId == null) {
            return;
        }

        positionsMs.put(normalizedAdId, Math.max(0L, positionMs));
    }

    public void bindActiveVideo(String adId) {
        play(adId);
    }

    public void pauseActiveVideo() {
        if (activeAdId != null) {
            pause(activeAdId);
        }
    }

    public String getActiveAdId() {
        return activeAdId;
    }

    public PlaybackState getActiveState() {
        if (activeAdId == null) {
            return PlaybackState.IDLE;
        }
        return getState(activeAdId);
    }

    public PlaybackState getState(String adId) {
        String normalizedAdId = normalizeAdId(adId);
        if (normalizedAdId == null) {
            return PlaybackState.IDLE;
        }
        PlaybackState state = playbackStates.get(normalizedAdId);
        return state == null ? PlaybackState.IDLE : state;
    }

    public long getActivePositionMs() {
        if (activeAdId == null) {
            return 0L;
        }
        return getPositionMs(activeAdId);
    }

    public long getPositionMs(String adId) {
        String normalizedAdId = normalizeAdId(adId);
        if (normalizedAdId == null) {
            return 0L;
        }
        Long positionMs = positionsMs.get(normalizedAdId);
        return positionMs == null ? 0L : positionMs;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    private void pauseCurrentIfReplacing(String nextAdId) {
        if (activeAdId != null && !activeAdId.equals(nextAdId)
                && getState(activeAdId) == PlaybackState.PLAYING) {
            playbackStates.put(activeAdId, PlaybackState.PAUSED);
        }
    }

    private void clearActiveIfMatches(String adId) {
        if (adId.equals(activeAdId)) {
            activeAdId = null;
        }
    }

    private String normalizeAdId(String adId) {
        if (adId == null) {
            return null;
        }

        String trimmedAdId = adId.trim();
        return trimmedAdId.isEmpty() ? null : trimmedAdId;
    }
}
