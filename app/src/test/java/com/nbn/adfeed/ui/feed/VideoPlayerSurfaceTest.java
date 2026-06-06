package com.nbn.adfeed.ui.feed;

import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.media3.ui.PlayerView;

import com.nbn.adfeed.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class VideoPlayerSurfaceTest {
    @Test
    public void feedVideoUsesTextureViewForReliableRecyclerViewRendering() {
        PlayerView playerView = inflatePlayerView(R.layout.item_ad_video, R.id.videoPlayerView);

        assertTrue(playerView.getVideoSurfaceView() instanceof TextureView);
        assertPlayerIsBehindCover(playerView, R.id.mediaImage);
    }

    @Test
    public void detailVideoUsesTextureViewForReliableRendering() {
        PlayerView playerView = inflatePlayerView(R.layout.activity_ad_detail, R.id.detailPlayerView);

        assertTrue(playerView.getVideoSurfaceView() instanceof TextureView);
        assertPlayerIsBehindCover(playerView, R.id.detailMedia);
    }

    private static PlayerView inflatePlayerView(int layoutId, int playerViewId) {
        ContextThemeWrapper context = new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(),
                androidx.appcompat.R.style.Theme_AppCompat
        );
        FrameLayout parent = new FrameLayout(context);
        View root = LayoutInflater.from(context).inflate(layoutId, parent, false);
        return root.findViewById(playerViewId);
    }

    private static void assertPlayerIsBehindCover(PlayerView playerView, int coverViewId) {
        ViewGroup parent = (ViewGroup) playerView.getParent();
        View cover = parent.findViewById(coverViewId);
        assertTrue(parent.indexOfChild(playerView) < parent.indexOfChild(cover));
    }
}
