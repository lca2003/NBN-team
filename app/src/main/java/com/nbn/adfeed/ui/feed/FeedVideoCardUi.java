package com.nbn.adfeed.ui.feed;

import android.view.View;

/** 统一管理视频卡封面、播放器与覆盖控件的可见状态。 */
final class FeedVideoCardUi {
    private FeedVideoCardUi() {
    }

    static void showPreparing(View cover,
                              View scrim,
                              View playButton,
                              View stateText,
                              View player) {
        setVisibility(cover, View.VISIBLE);
        setVisibility(scrim, View.GONE);
        setVisibility(playButton, View.GONE);
        setVisibility(stateText, View.GONE);
        setVisibility(player, View.VISIBLE);
    }

    static void showPlaying(View cover,
                            View scrim,
                            View playButton,
                            View stateText,
                            View player) {
        setVisibility(cover, View.GONE);
        setVisibility(scrim, View.GONE);
        setVisibility(playButton, View.GONE);
        setVisibility(stateText, View.GONE);
        setVisibility(player, View.VISIBLE);
    }

    static void showCover(View cover,
                          View scrim,
                          View playButton,
                          View stateText,
                          View player) {
        setVisibility(cover, View.VISIBLE);
        setVisibility(scrim, View.VISIBLE);
        setVisibility(playButton, View.VISIBLE);
        setVisibility(stateText, View.VISIBLE);
        setVisibility(player, View.GONE);
    }

    private static void setVisibility(View view, int visibility) {
        if (view != null) {
            view.setVisibility(visibility);
        }
    }
}
