package com.nbn.adfeed.ui.feed;

import android.content.Context;
import android.view.ContextThemeWrapper;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.analytics.event.AdAnalyticsEventCounts;
import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionState;
import com.nbn.adfeed.data.repository.AdRepository;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class FeedInteractionDelegatePersistenceTest {
    private static final String DATABASE_NAME = "ad_analytics_events.db";

    private final Context context = themedContext();

    @After
    public void tearDown() {
        RuntimeEnvironment.getApplication().deleteDatabase(DATABASE_NAME);
    }

    @Test
    public void likeClickPersistsAsAnalyticsEvent() {
        AnalyticsTracker tracker = new AnalyticsTracker(context);
        FeedInteractionDelegate delegate = delegate(tracker);
        AdItem ad = ad("feed_like_persist_001");

        delegate.onLikeClick(ad, 0);

        Map<String, AdAnalyticsEventCounts> countsByAdId = tracker.loadCountsByAdId();
        AdAnalyticsEventCounts counts = countsByAdId.get(ad.getId());

        assertEquals(1, counts.getLikeDelta());
    }

    private static FeedInteractionDelegate delegate(AnalyticsTracker tracker) {
        Context context = themedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        FeedAdapter adapter = new FeedAdapter(noopListener());
        FeedInteractionDelegate delegate = new FeedInteractionDelegate();
        delegate.bind(
                context,
                recyclerView,
                adapter,
                InteractionStore.get(),
                new AdCatalog(new AdRepository() { }),
                tracker
        );
        return delegate;
    }

    private static ContextThemeWrapper themedContext() {
        return new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(),
                androidx.appcompat.R.style.Theme_AppCompat
        );
    }

    private static AdItem ad(String id) {
        return new AdItem(
                id,
                "测试广告",
                "NBN",
                "精选",
                "测试摘要",
                AdContentType.LARGE_IMAGE,
                Collections.singletonList("测试"),
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
