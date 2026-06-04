package com.nbn.adfeed.video.player;

import android.os.Looper;
import android.view.ContextThemeWrapper;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.ui.PlayerView;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class Media3VideoPlayerControllerTest {
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
            playbackState = playlist.isEmpty() ? Player.STATE_IDLE : Player.STATE_READY;
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
        protected ListenableFuture<?> handleSetMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
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

        private List<MediaItemData> buildPlaylist() {
            List<MediaItemData> mediaItemData = new ArrayList<>();
            for (int index = 0; index < playlist.size(); index++) {
                mediaItemData.add(new MediaItemData.Builder("item-" + index)
                        .setMediaItem(playlist.get(index))
                        .setIsSeekable(true)
                        .setDefaultPositionUs(0L)
                        .setDurationUs(1_000_000L)
                        .build());
            }
            return mediaItemData;
        }
    }
}
