package com.nbn.adfeed.video.player;

import android.os.Looper;
import android.view.ContextThemeWrapper;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class Media3VideoPlayerControllerUriTest {
    @Test
    public void relativeRawPathResolvesToPackagedVideoResource() {
        FakePlayer fakePlayer = new FakePlayer();
        Media3VideoPlayerController controller = new Media3VideoPlayerController(
                RuntimeEnvironment.getApplication(),
                new VideoPlaybackManager(),
                context -> fakePlayer
        );

        assertTrue(controller.play("ad-1", "raw/ad_video_headphones.mp4", playerView()));
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(
                "rawresource:///" + R.raw.ad_video_headphones,
                fakePlayer.playlist.get(0).localConfiguration.uri.toString()
        );
    }

    @Test
    public void knownAdUsesPackagedVideoWithoutWaitingForRemoteFailure() {
        FakePlayer fakePlayer = new FakePlayer();
        Media3VideoPlayerController controller = new Media3VideoPlayerController(
                RuntimeEnvironment.getApplication(),
                new VideoPlaybackManager(),
                context -> fakePlayer
        );

        assertTrue(controller.play(
                "ad_027",
                "https://res.cloudinary.com/demo/video/upload/du_12/sea_turtle.mp4",
                playerView()
        ));
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(
                "rawresource:///" + R.raw.ad_video_study_ai,
                fakePlayer.playlist.get(0).localConfiguration.uri.toString()
        );
        assertTrue(fakePlayer.playWhenReady);
    }

    @Test
    public void playbackEndResetsSavedPositionWithoutRequiringCallback() {
        VideoPlaybackManager manager = new VideoPlaybackManager();
        FakePlayer fakePlayer = new FakePlayer();
        Media3VideoPlayerController controller = new Media3VideoPlayerController(
                RuntimeEnvironment.getApplication(),
                manager,
                context -> fakePlayer
        );

        assertTrue(controller.play("ad_018", "raw/ad_video_local_sports.mp4", playerView()));
        shadowOf(Looper.getMainLooper()).idle();

        fakePlayer.endPlaybackAt(2_000L);
        shadowOf(Looper.getMainLooper()).idle();

        assertNull(manager.getActiveAdId());
        assertEquals(0L, manager.getPositionMs("ad_018"));
    }

    @Test
    public void activeVideoRepeatsUntilVisibilityRuleStopsIt() {
        FakePlayer fakePlayer = new FakePlayer();
        Media3VideoPlayerController controller = new Media3VideoPlayerController(
                RuntimeEnvironment.getApplication(),
                new VideoPlaybackManager(),
                context -> fakePlayer
        );

        assertTrue(controller.play("ad_018", "raw/ad_video_local_sports.mp4", playerView()));
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(Player.REPEAT_MODE_ONE, fakePlayer.repeatMode);
    }

    private static PlayerView playerView() {
        return new PlayerView(new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(),
                androidx.appcompat.R.style.Theme_AppCompat
        ));
    }

    private static final class FakePlayer extends SimpleBasePlayer {
        private final List<MediaItem> playlist = new ArrayList<>();
        private final Player.Commands commands = new Player.Commands.Builder()
                .addAllCommands()
                .build();
        private boolean playWhenReady;
        private int playbackState = Player.STATE_IDLE;
        private int repeatMode = Player.REPEAT_MODE_OFF;
        private float volume;
        private PlaybackException playerError;
        private long positionMs;

        private FakePlayer() {
            super(Looper.getMainLooper());
        }

        @Override
        protected State getState() {
            State.Builder builder = new State.Builder()
                    .setAvailableCommands(commands)
                    .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    .setPlaybackState(playbackState)
                    .setPlayerError(playerError)
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
            playlist.addAll(mediaItems);
            playerError = null;
            positionMs = Math.max(0L, startPositionMs);
            playbackState = Player.STATE_IDLE;
            invalidateState();
            return Futures.immediateFuture(null);
        }

        private void failPlayback() {
            playWhenReady = false;
            playbackState = Player.STATE_IDLE;
            playerError = new PlaybackException(
                    "network unavailable",
                    null,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
            );
            invalidateState();
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
