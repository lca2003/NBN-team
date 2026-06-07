package com.nbn.adfeed.ui.feed;

import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class FeedVideoCardUiTest {
    @Test
    public void preparingKeepsCoverVisibleUntilPlayerReallyStarts() {
        VideoViews views = new VideoViews();

        FeedVideoCardUi.showPreparing(
                views.cover,
                views.scrim,
                views.playButton,
                views.stateText,
                views.player
        );

        assertEquals(View.VISIBLE, views.cover.getVisibility());
        assertEquals(View.VISIBLE, views.player.getVisibility());
    }

    @Test
    public void playingShowsPlayerAndHidesCover() {
        VideoViews views = new VideoViews();

        FeedVideoCardUi.showPlaying(
                views.cover,
                views.scrim,
                views.playButton,
                views.stateText,
                views.player
        );

        assertEquals(View.GONE, views.cover.getVisibility());
        assertEquals(View.VISIBLE, views.player.getVisibility());
    }

    private static final class VideoViews {
        private final View cover = view();
        private final View scrim = view();
        private final View playButton = view();
        private final View stateText = view();
        private final View player = view();

        private static View view() {
            return new View(RuntimeEnvironment.getApplication());
        }
    }
}
