package com.nbn.adfeed.video.player;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.nbn.adfeed.R;
import com.nbn.adfeed.video.VideoPlaybackManager;

public final class Media3VideoPlayerController {
    public interface PlaybackCallback {
        void onBuffering(String adId);

        void onPlaying(String adId);

        void onPaused(String adId);

        void onEnded(String adId);

        void onError(String adId, String message);
    }

    interface PlayerFactory {
        Player create(Context context);
    }

    private final Context appContext;
    private final VideoPlaybackManager playbackManager;
    private final PlayerFactory playerFactory;
    private Player player;
    private PlayerView activePlayerView;
    private String activeVideoUri;
    private String fallbackVideoUri;
    private PlaybackCallback callback;
    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            String activeAdId = playbackManager.getActiveAdId();
            if (activeAdId == null) {
                return;
            }
            if (playbackState == Player.STATE_BUFFERING) {
                if (callback != null) {
                    callback.onBuffering(activeAdId);
                }
            } else if (playbackState == Player.STATE_ENDED) {
                playbackManager.stop(activeAdId, 0L);
                if (callback != null) {
                    callback.onEnded(activeAdId);
                }
            }
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            String activeAdId = playbackManager.getActiveAdId();
            if (activeAdId == null || callback == null) {
                return;
            }
            if (isPlaying) {
                callback.onPlaying(activeAdId);
            } else {
                callback.onPaused(activeAdId);
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            String activeAdId = playbackManager.getActiveAdId();
            if (activeAdId == null) {
                return;
            }
            if (tryFallbackPlayback()) {
                return;
            }
            playbackManager.stop(activeAdId, currentPositionMs(player));
            if (callback != null) {
                callback.onError(activeAdId, error == null ? "播放失败" : error.getMessage());
            }
        }
    };

    public Media3VideoPlayerController(Context context, VideoPlaybackManager playbackManager) {
        this(context, playbackManager, appContext -> new ExoPlayer.Builder(appContext).build());
    }

    Media3VideoPlayerController(Context context, VideoPlaybackManager playbackManager, PlayerFactory playerFactory) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (playbackManager == null) {
            throw new IllegalArgumentException("playbackManager must not be null");
        }
        Context applicationContext = context.getApplicationContext();
        this.appContext = applicationContext == null ? context : applicationContext;
        this.playbackManager = playbackManager;
        this.playerFactory = playerFactory == null
                ? appContext -> new ExoPlayer.Builder(appContext).build()
                : playerFactory;
    }

    public boolean play(String adId, String videoUri, PlayerView playerView) {
        String normalizedAdId = normalize(adId);
        String normalizedVideoUri = resolveVideoUri(videoUri);
        if (normalizedAdId == null || normalizedVideoUri == null || playerView == null) {
            return false;
        }

        Player mediaPlayer = ensurePlayer();
        saveCurrentPositionIfSwitching(normalizedAdId, mediaPlayer);
        attachPlayerView(playerView, mediaPlayer);
        applyMutedState(mediaPlayer);
        fallbackVideoUri = packagedFallbackVideoUri(normalizedAdId);

        if (isCurrentMedia(normalizedAdId, normalizedVideoUri, mediaPlayer)) {
            playbackManager.resume(normalizedAdId);
            mediaPlayer.play();
            return true;
        }

        long resumePositionMs = playbackManager.getPositionMs(normalizedAdId);
        playbackManager.play(normalizedAdId);
        activeVideoUri = normalizedVideoUri;
        mediaPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(normalizedVideoUri)));
        mediaPlayer.prepare();
        if (resumePositionMs > 0L) {
            mediaPlayer.seekTo(resumePositionMs);
        }
        mediaPlayer.play();
        return true;
    }

    public void setPlaybackCallback(PlaybackCallback callback) {
        this.callback = callback;
    }

    public void pause(String adId) {
        String normalizedAdId = normalize(adId);
        if (normalizedAdId == null) {
            return;
        }

        Player mediaPlayer = player;
        if (mediaPlayer != null && normalizedAdId.equals(playbackManager.getActiveAdId())) {
            mediaPlayer.pause();
            playbackManager.pause(normalizedAdId, currentPositionMs(mediaPlayer));
            return;
        }

        playbackManager.pause(normalizedAdId);
    }

    public void pauseActive() {
        String activeAdId = playbackManager.getActiveAdId();
        if (activeAdId != null) {
            pause(activeAdId);
        }
    }

    public boolean resume(String adId, String videoUri, PlayerView playerView) {
        return play(adId, videoUri, playerView);
    }

    public void releaseOffscreen(String adId) {
        String normalizedAdId = normalize(adId);
        if (normalizedAdId == null) {
            return;
        }

        Player mediaPlayer = player;
        if (mediaPlayer != null && normalizedAdId.equals(playbackManager.getActiveAdId())) {
            long positionMs = currentPositionMs(mediaPlayer);
            mediaPlayer.pause();
            mediaPlayer.stop();
            mediaPlayer.clearMediaItems();
            detachActiveView();
            activeVideoUri = null;
            fallbackVideoUri = null;
            playbackManager.releaseOffscreen(normalizedAdId, positionMs);
            return;
        }

        playbackManager.releaseOffscreen(normalizedAdId);
    }

    public void setMuted(boolean muted) {
        playbackManager.setMuted(muted);
        Player mediaPlayer = player;
        if (mediaPlayer != null) {
            applyMutedState(mediaPlayer);
        }
    }

    public boolean isMuted() {
        return playbackManager.isMuted();
    }

    public String getActiveAdId() {
        return playbackManager.getActiveAdId();
    }

    public void release() {
        Player mediaPlayer = player;
        if (mediaPlayer != null) {
            String activeAdId = playbackManager.getActiveAdId();
            if (activeAdId != null) {
                playbackManager.updatePositionMs(activeAdId, currentPositionMs(mediaPlayer));
            }
            mediaPlayer.removeListener(playerListener);
            mediaPlayer.release();
            player = null;
        }
        detachActiveView();
        activeVideoUri = null;
        fallbackVideoUri = null;
        playbackManager.releaseAll();
    }

    private Player ensurePlayer() {
        if (player == null) {
            player = playerFactory.create(appContext);
            player.addListener(playerListener);
            player.setRepeatMode(Player.REPEAT_MODE_OFF);
            applyMutedState(player);
        }
        return player;
    }

    private void saveCurrentPositionIfSwitching(String nextAdId, Player mediaPlayer) {
        String activeAdId = playbackManager.getActiveAdId();
        if (activeAdId != null && !activeAdId.equals(nextAdId)) {
            playbackManager.pause(activeAdId, currentPositionMs(mediaPlayer));
        }
    }

    private void attachPlayerView(PlayerView playerView, Player mediaPlayer) {
        if (activePlayerView != null && activePlayerView != playerView) {
            activePlayerView.setPlayer(null);
        }
        activePlayerView = playerView;
        activePlayerView.setPlayer(mediaPlayer);
    }

    private void detachActiveView() {
        if (activePlayerView != null) {
            activePlayerView.setPlayer(null);
            activePlayerView = null;
        }
    }

    private boolean isCurrentMedia(String adId, String videoUri, Player mediaPlayer) {
        return adId.equals(playbackManager.getActiveAdId())
                && videoUri.equals(activeVideoUri)
                && mediaPlayer.getMediaItemCount() > 0;
    }

    private boolean tryFallbackPlayback() {
        Player mediaPlayer = player;
        if (mediaPlayer == null || fallbackVideoUri == null || fallbackVideoUri.equals(activeVideoUri)) {
            return false;
        }

        long resumePositionMs = currentPositionMs(mediaPlayer);
        activeVideoUri = fallbackVideoUri;
        mediaPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(fallbackVideoUri)));
        mediaPlayer.prepare();
        if (resumePositionMs > 0L) {
            mediaPlayer.seekTo(resumePositionMs);
        }
        mediaPlayer.play();
        return true;
    }

    private String packagedFallbackVideoUri(String adId) {
        int resourceId;
        switch (adId) {
            case "ad_003":
            case "ad_015":
                resourceId = R.raw.ad_video_headphones;
                break;
            case "ad_007":
            case "ad_027":
                resourceId = R.raw.ad_video_study_ai;
                break;
            case "ad_018":
            case "ad_022":
                resourceId = R.raw.ad_video_local_sports;
                break;
            default:
                return null;
        }
        return "rawresource:///" + resourceId;
    }

    private void applyMutedState(Player mediaPlayer) {
        mediaPlayer.setVolume(playbackManager.isMuted() ? 0f : 1f);
    }

    private long currentPositionMs(Player mediaPlayer) {
        return Math.max(0L, mediaPlayer.getCurrentPosition());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }

    private String resolveVideoUri(String videoUri) {
        String normalizedVideoUri = normalize(videoUri);
        if (normalizedVideoUri == null || !normalizedVideoUri.startsWith("raw/")) {
            return normalizedVideoUri;
        }

        String resourceName = normalizedVideoUri.substring("raw/".length());
        int extensionIndex = resourceName.lastIndexOf('.');
        if (extensionIndex > 0) {
            resourceName = resourceName.substring(0, extensionIndex);
        }
        int resourceId = appContext.getResources().getIdentifier(
                resourceName,
                "raw",
                appContext.getPackageName()
        );
        return resourceId == 0 ? normalizedVideoUri : "rawresource:///" + resourceId;
    }
}
