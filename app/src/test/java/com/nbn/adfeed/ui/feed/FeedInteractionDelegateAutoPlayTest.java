package com.nbn.adfeed.ui.feed;

import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionState;
import com.nbn.adfeed.video.VideoPlaybackManager;
import com.nbn.adfeed.video.player.Media3VideoPlayerController;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class FeedInteractionDelegateAutoPlayTest {
    @Test
    public void autoPlayMostVisibleVideoStartsVisibleVideoCard() {
        ContextThemeWrapper context = themedContext();
        FeedAdapter adapter = new FeedAdapter(noopListener());
        AdItem videoAd = videoAd("ad_003");
        adapter.submit(Collections.singletonList(videoAd));

        RecyclerView recyclerView = new RecyclerView(context);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        );
        recyclerView.layout(0, 0, 1080, 1920);

        VideoPlaybackManager playbackManager = new VideoPlaybackManager();
        Media3VideoPlayerController videoController = new Media3VideoPlayerController(
                context,
                playbackManager
        );
        FeedInteractionDelegate delegate = new FeedInteractionDelegate();
        delegate.bind(context, recyclerView, adapter, null, null, null);
        delegate.bindVideo(playbackManager, videoController);

        delegate.autoPlayMostVisibleVideo(layoutManager);

        assertEquals("ad_003", playbackManager.getActiveAdId());
    }

    private static ContextThemeWrapper themedContext() {
        return new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(),
                androidx.appcompat.R.style.Theme_AppCompat
        );
    }

    private static AdItem videoAd(String id) {
        return new AdItem(
                id,
                "测试视频",
                "NBN",
                "精选",
                "测试描述",
                "测试摘要",
                null,
                "raw/ad_video_headphones.mp4",
                AdContentType.VIDEO,
                Collections.singletonList("视频"),
                new InteractionState()
        );
    }

    private static FeedInteractionListener noopListener() {
        return new FeedInteractionListener() {
            @Override
            public void onCardClick(AdItem ad, int position) {
            }

            @Override
            public void onLikeClick(AdItem ad, int position) {
            }

            @Override
            public void onCollectClick(AdItem ad, int position) {
            }

            @Override
            public void onShareClick(AdItem ad, int position) {
            }

            @Override
            public void onTagClick(AdItem ad, String tag, int position) {
            }

            @Override
            public void onVideoPlayClick(AdItem ad, int position) {
            }

            @Override
            public void onVideoCardDetached(AdItem ad) {
            }
        };
    }
}
