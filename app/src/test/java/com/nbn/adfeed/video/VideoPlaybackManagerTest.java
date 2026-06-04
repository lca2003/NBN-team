package com.nbn.adfeed.video;

import org.junit.Test;

import static com.nbn.adfeed.video.VideoPlaybackManager.PlaybackState.IDLE;
import static com.nbn.adfeed.video.VideoPlaybackManager.PlaybackState.PAUSED;
import static com.nbn.adfeed.video.VideoPlaybackManager.PlaybackState.PLAYING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class VideoPlaybackManagerTest {
    @Test
    public void playNewAdPausesPreviousActiveAd() {
        VideoPlaybackManager manager = new VideoPlaybackManager();

        manager.play("ad-1");
        manager.updatePositionMs("ad-1", 1_200L);
        manager.play("ad-2");

        assertEquals("ad-2", manager.getActiveAdId());
        assertEquals(PLAYING, manager.getState("ad-2"));
        assertEquals(PAUSED, manager.getState("ad-1"));
        assertEquals(1_200L, manager.getPositionMs("ad-1"));
    }

    @Test
    public void pauseAndResumeKeepSavedPosition() {
        VideoPlaybackManager manager = new VideoPlaybackManager();

        manager.play("ad-1");
        manager.pause("ad-1", 4_500L);
        manager.resume("ad-1");

        assertEquals("ad-1", manager.getActiveAdId());
        assertEquals(PLAYING, manager.getActiveState());
        assertEquals(4_500L, manager.getActivePositionMs());
    }

    @Test
    public void releaseOffscreenClearsActivePlaybackButKeepsPosition() {
        VideoPlaybackManager manager = new VideoPlaybackManager();

        manager.play("ad-1");
        manager.releaseOffscreen("ad-1", 2_750L);

        assertNull(manager.getActiveAdId());
        assertEquals(IDLE, manager.getState("ad-1"));
        assertEquals(2_750L, manager.getPositionMs("ad-1"));

        manager.resume("ad-1");

        assertEquals("ad-1", manager.getActiveAdId());
        assertEquals(PLAYING, manager.getState("ad-1"));
        assertEquals(2_750L, manager.getPositionMs("ad-1"));
    }

    @Test
    public void releaseOffscreenForPausedNonActiveAdKeepsCurrentActiveVideo() {
        VideoPlaybackManager manager = new VideoPlaybackManager();

        manager.play("ad-1");
        manager.updatePositionMs("ad-1", 900L);
        manager.play("ad-2");
        manager.releaseOffscreen("ad-1", 1_100L);

        assertEquals("ad-2", manager.getActiveAdId());
        assertEquals(PLAYING, manager.getActiveState());
        assertEquals(IDLE, manager.getState("ad-1"));
        assertEquals(1_100L, manager.getPositionMs("ad-1"));
    }

    @Test
    public void releaseAllClearsEveryPlaybackStateButKeepsPositions() {
        VideoPlaybackManager manager = new VideoPlaybackManager();

        manager.play("ad-1");
        manager.updatePositionMs("ad-1", 600L);
        manager.play("ad-2");
        manager.updatePositionMs("ad-2", 1_400L);
        manager.releaseAll();

        assertNull(manager.getActiveAdId());
        assertEquals(IDLE, manager.getState("ad-1"));
        assertEquals(IDLE, manager.getState("ad-2"));
        assertEquals(600L, manager.getPositionMs("ad-1"));
        assertEquals(1_400L, manager.getPositionMs("ad-2"));
    }

    @Test
    public void stopClearsActivePlaybackButKeepsPosition() {
        VideoPlaybackManager manager = new VideoPlaybackManager();

        manager.play("ad-1");
        manager.stop("ad-1", 3_000L);

        assertNull(manager.getActiveAdId());
        assertEquals(IDLE, manager.getState("ad-1"));
        assertEquals(3_000L, manager.getPositionMs("ad-1"));
    }

    @Test
    public void mutedStateCanBeToggled() {
        VideoPlaybackManager manager = new VideoPlaybackManager();

        assertTrue(manager.isMuted());

        manager.setMuted(false);
        assertFalse(manager.isMuted());

        manager.setMuted(true);
        assertTrue(manager.isMuted());
    }

    @Test
    public void invalidAdIdsAreIgnoredWithoutClearingActiveState() {
        VideoPlaybackManager manager = new VideoPlaybackManager();

        manager.play(null);
        manager.play("  ");
        manager.pause(null, 100L);
        manager.resume("");
        manager.stop(" ");
        manager.releaseOffscreen(null);
        manager.updatePositionMs("", 200L);

        assertNull(manager.getActiveAdId());
        assertEquals(IDLE, manager.getState(null));
        assertEquals(0L, manager.getPositionMs(" "));

        manager.play("ad-1");
        manager.releaseOffscreen("");
        manager.stop(null);

        assertEquals("ad-1", manager.getActiveAdId());
        assertEquals(PLAYING, manager.getActiveState());
    }
}
