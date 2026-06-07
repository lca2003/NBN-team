package com.nbn.adfeed.video.player;

import android.os.Looper;
import android.view.ContextThemeWrapper;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.ui.PlayerView;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.nbn.adfeed.R;
import com.nbn.adfeed.video.VideoPlaybackManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static com.nbn.adfeed.video.VideoPlaybackManager.PlaybackState.IDLE;
import static com.nbn.adfeed.video.VideoPlaybackManager.PlaybackState.PAUSED;
import static com.nbn.adfeed.video.VideoPlaybackManager.PlaybackState.PLAYING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class Media3VideoPlayerControllerUriTest {
    @Test
    public void relativeRawPathResolvesToPackagedVideoResource() {
        FakePlayer fakePlayer = new FakePlayer();
        Media3VideoPlayerController controller = controller(new VideoPlaybackManager(), fakePlayer);

        assertTrue(controller.play("ad-1", "raw/ad_video_headphones.mp4", playerView()));
        idleMainLooper();

        assertEquals(
                "rawresource:///" + R.raw.ad_video_headphones,
                fakePlayer.playlist.get(0).localConfiguration.uri.toString()
        );
    }

    @Test
    public void knownAdUsesPackagedVideoWithoutWaitingForRemoteFailure() {
        FakePlayer fakePlayer = new FakePlayer();
        Media3VideoPlayerController controller = controller(new VideoPlaybackManager(), fakePlayer);

        assertTrue(controller.play(
                "ad_027",
                "https://res.cloudinary.com/demo/video/upload/du_12/sea_turtle.mp4",
                playerView()
        ));
        idleMainLooper();

        assertEquals(
                "rawresource:///" + R.raw.ad_video_study_ai,
                fakePlayer.playlist.get(0).localConfiguration.uri.toString()
        );
        assertTrue(fakePlayer.playWhenReady);
    }

    @Test
    public void switchingPlayerViewDetachesPreviousViewAndSavesProgress() {
        VideoPlaybackManager manager = new VideoPlaybackManager();
        FakePlayer fakePlayer = new FakePlayer();
        Media3VideoPlayerController controller = controller(manager, fakePlayer);
        PlayerView firstView = playerView();
        PlayerView secondView = playerView();

        assertTrue(controller.play("ad-1", "https://example.com/a.mp4", firstView));
        idleMainLooper();
        fakePlayer.setCurrentPositionForTest(1_250L);

        assertTrue(controller.play("ad-2", "https://example.com/b.mp4", secondView));
        idleMainLooper();

        assertNull(firstView.getPlayer());
        assertSame(fakePlayer, secondView.getPlayer());
        assertEquals(PAUSED, manager.getState("ad-1"));
        assertEquals(1_250L, manager.getPositionMs("ad-1"));
        assertEquals(PLAYING, manager.getState("ad-2"));
    }

    @Test
    public void releaseOffscreenDetachesPlayerViewAndAllowsResumeFromSavedPosition() {
        VideoPlaybackManager manager = new VideoPlaybackManager();
        FakePlayer fakePlayer = new FakePlayer();
        Media3VideoPlayerController controller = controller(manager, fakePlayer);
        PlayerView playerView = playerView();

        assertTrue(controller.play("ad-1", "https://example.com/a.mp4", playerView));
        idleMainLooper();
        fakePlayer.setCurrentPositionForTest(2_750L);

        controller.releaseOffscreen("ad-1");
        idleMainLooper();

        assertNull(playerView.getPlayer());
        assertNull(manager.getActiveAdId());
        assertEquals(IDLE, manager.getState("ad-1"));
        assertEquals(2_750L, manager.getPositionMs("ad-1"));

        assertTrue(controller.resume("ad-1", "https://example.com/a.mp4", playerView));
        idleMainLooper();

        assertSame(fakePlayer, playerView.getPlayer());
        assertEquals(2_750L, fakePlayer.lastSeekPositionMs);
        assertEquals(PLAYING, manager.getState("ad-1"));
    }

    @Test
    public void releaseDetachesPlayerViewAndReleasesUnderlyingPlayer() {
        VideoPlaybackManager manager = new VideoPlaybackManager();
        FakePlayer fakePlayer = new FakePlayer();
        Media3VideoPlayerController controller = controller(manager, fakePlayer);
        PlayerView playerView = playerView();

        assertTrue(controller.play("ad-1", "https://example.com/a.mp4", playerView));
        idleMainLooper();
        fakePlayer.setCurrentPositionForTest(3_300L);

        controller.release();
        idleMainLooper();

        assertNull(playerView.getPlayer());
        assertTrue(fakePlayer.released);
        assertNull(manager.getActiveAdId());
        assertEquals(IDLE, manager.getState("ad-1"));
        assertEquals(3_300L, manager.getPositionMs("ad-1"));
    }

    @Test
    public void bufferingAfterPlayRequestDoesNotEmitPaused() {
        VideoPlaybackManager manager = new VideoPlaybackManager();
        FakePlayer fakePlayer = new FakePlayer();
        fakePlayer.setBufferingOnPrepareForTest(true);
        Media3VideoPlayerController controller = controller(manager, fakePlayer);
        List<String> events = new ArrayList<>();
        controller.setPlaybackCallback(new Media3VideoPlayerController.PlaybackCallback() {
            @Override
            public void onBuffering(String adId) {
                events.add("buffering:" + adId);
            }

            @Override
            public void onPlaying(String adId) {
                events.add("playing:" + adId);
            }

            @Override
            public void onPaused(String adId) {
                events.add("paused:" + adId);
            }

            @Override
            public void onEnded(String adId) {
                events.add("ended:" + adId);
            }

            @Override
            public void onError(String adId, String message) {
                events.add("error:" + adId);
            }
        });

        assertTrue(controller.play("ad-1", "rawresource:///1", playerView()));
        idleMainLooper();

        assertTrue(events.toString(), events.contains("buffering:ad-1"));
        assertFalse(events.toString(), events.contains("paused:ad-1"));
    }

    @Test
    public void playbackEndResetsSavedPositionWithoutRequiringCallback() {
        VideoPlaybackManager manager = new VideoPlaybackManager();
        FakePlayer fakePlayer = new FakePlayer();
        Media3VideoPlayerController controller = controller(manager, fakePlayer);

        assertTrue(controller.play("ad_018", "raw/ad_video_local_sports.mp4", playerView()));
        idleMainLooper();

        fakePlayer.endPlaybackAt(2_000L);
        idleMainLooper();

        assertNull(manager.getActiveAdId());
        assertEquals(0L, manager.getPositionMs("ad_018"));
    }

    @Test
    public void activeVideoRepeatsUntilVisibilityRuleStopsIt() {
        FakePlayer fakePlayer = new FakePlayer();
        Media3VideoPlayerController controller = controller(new VideoPlaybackManager(), fakePlayer);

        assertTrue(controller.play("ad_018", "raw/ad_video_local_sports.mp4", playerView()));
        idleMainLooper();

        assertEquals(Player.REPEAT_MODE_ONE, fakePlayer.repeatMode);
    }

    private static Media3VideoPlayerController controller(VideoPlaybackManager manager, FakePlayer fakePlayer) {
        return new Media3VideoPlayerController(
                RuntimeEnvironment.getApplication(),
                manager,
                context -> fakePlayer
        );
    }

    private static PlayerView playerView() {
        return new PlayerView(new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(),
                androidx.appcompat.R.style.Theme_AppCompat
        ));
    }

    private static void idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static final class FakePlayer extends SimpleBasePlayer {
        private final List<MediaItem> playlist = new ArrayList<>();
        private final Player.Commands commands = new Player.Commands.Builder()
                .addAllCommands()
                .build();
        private long positionMs;
        private boolean playWhenReady;
        private int playbackState = Player.STATE_IDLE;
        private int repeatMode = Player.REPEAT_MODE_OFF;
        private float volume;
        private boolean released;
        private boolean bufferingOnPrepare;
        private long lastSeekPositionMs = -1L;

        private FakePlayer() {
            super(Looper.getMainLooper());
        }

        @Override
        protected State getState() {
            State.Builder builder = new State.Builder()
                    .setAvailableCommands(commands)
                    .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    .setPlaybackState(playbackState)
                    .setRepeatMode(repeatMode)
                    .setVolume(volume)
                    .setContentPositionMs(positionMs);
            if (!playlist.isEmpty()) {
                builder.setPlaylist(buildPlaylist());
                builder.setCurrentMediaItemIndex(0);
            }
            return builder.build();
        }

        @Override
        protected ListenableFuture<?> handleSetPlayWhenReady(boolean nextPlayWhenReady) {
            playWhenReady = nextPlayWhenReady;
            if (nextPlayWhenReady && !playlist.isEmpty() && playbackState == Player.STATE_IDLE) {
                playbackState = Player.STATE_READY;
            }
            invalidateState();
            return Futures.immediateFuture(null);
        }

        @Override
        protected ListenableFuture<?> handlePrepare() {
            playbackState = playlist.isEmpty()
                    ? Player.STATE_IDLE
                    : bufferingOnPrepare ? Player.STATE_BUFFERING : Player.STATE_READY;
            invalidateState();
            return Futures.immediateFuture(null);
        }

        @Override
        protected ListenableFuture<?> handleStop() {
            playWhenReady = false;
            playbackState = Player.STATE_IDLE;
            invalidateState();
            return Futures.immediateFuture(null);
        }

        @Override
        protected ListenableFuture<?> handleRelease() {
            released = true;
            playWhenReady = false;
            playbackState = Player.STATE_IDLE;
            invalidateState();
            return Futures.immediateFuture(null);
        }

        @Override
        protected ListenableFuture<?> handleSetRepeatMode(int nextRepeatMode) {
            repeatMode = nextRepeatMode;
            invalidateState();
            return Futures.immediateFuture(null);
        }

        @Override
        protected ListenableFuture<?> handleSetVolume(float nextVolume) {
            volume = nextVolume;
            invalidateState();
            return Futures.immediateFuture(null);
        }

        @Override
        protected ListenableFuture<?> handleSetVideoOutput(Object videoOutput) {
            return Futures.immediateFuture(null);
        }

        @Override
        protected ListenableFuture<?> handleClearVideoOutput(Object videoOutput) {
            return Futures.immediateFuture(null);
        }

        @Override
        protected ListenableFuture<?> handleSetMediaItems(
                List<MediaItem> mediaItems,
                int startIndex,
                long startPositionMs
        ) {
            playlist.clear();
            if (mediaItems != null) {
                playlist.addAll(mediaItems);
            }
            positionMs = Math.max(0L, startPositionMs);
            playbackState = Player.STATE_IDLE;
            invalidateState();
            return Futures.immediateFuture(null);
        }

        @Override
        protected ListenableFuture<?> handleRemoveMediaItems(int fromIndex, int toIndex) {
            if (!playlist.isEmpty() && fromIndex < toIndex) {
                playlist.subList(Math.max(0, fromIndex), Math.min(toIndex, playlist.size())).clear();
            }
            if (playlist.isEmpty()) {
                positionMs = 0L;
            }
            invalidateState();
            return Futures.immediateFuture(null);
        }

        @Override
        protected ListenableFuture<?> handleSeek(int mediaItemIndex, long nextPositionMs, int seekCommand) {
            lastSeekPositionMs = Math.max(0L, nextPositionMs);
            positionMs = Math.max(0L, nextPositionMs);
            invalidateState();
            return Futures.immediateFuture(null);
        }

        private void setCurrentPositionForTest(long nextPositionMs) {
            positionMs = Math.max(0L, nextPositionMs);
            invalidateState();
        }

        private void setBufferingOnPrepareForTest(boolean bufferingOnPrepare) {
            this.bufferingOnPrepare = bufferingOnPrepare;
        }

        private void endPlaybackAt(long nextPositionMs) {
            positionMs = Math.max(0L, nextPositionMs);
            playWhenReady = false;
            playbackState = Player.STATE_ENDED;
            invalidateState();
        }

        private List<MediaItemData> buildPlaylist() {
            List<MediaItemData> mediaItems = new ArrayList<>();
            for (int index = 0; index < playlist.size(); index++) {
                mediaItems.add(new MediaItemData.Builder("item-" + index)
                        .setMediaItem(playlist.get(index))
                        .setIsSeekable(true)
                        .setDefaultPositionUs(0L)
                        .setDurationUs(1_000_000L)
                        .build());
            }
            return mediaItems;
        }
    }
}
