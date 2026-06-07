package com.nbn.adfeed.analytics.event;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class AdAnalyticsEventStoreTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @After
    public void tearDown() {
        context.deleteDatabase(AdAnalyticsEventContract.DATABASE_NAME);
    }

    @Test
    public void loadCountsByAdIdAggregatesInteractionEventsAsDeltas() {
        AdAnalyticsEventStore store = new AdAnalyticsEventStore(context);

        store.insertLike("ad_001", 100L);
        store.insertLike("ad_001", 101L);
        store.insertUnlike("ad_001", 102L);
        store.insertCollect("ad_001", 103L);
        store.insertUncollect("ad_001", 104L);
        store.insertCollect("ad_001", 105L);
        store.insertShare("ad_001", 106L);
        store.insertShare("ad_001", 107L);
        store.insertExposure("ad_001", 108L, 0.8f, 1000L);
        store.insertClick("ad_001", 109L);

        Map<String, AdAnalyticsEventCounts> countsByAdId = store.loadCountsByAdId();
        AdAnalyticsEventCounts counts = countsByAdId.get("ad_001");

        assertEquals(1, counts.getExposureCount());
        assertEquals(1, counts.getClickCount());
        assertEquals(1, counts.getLikeDelta());
        assertEquals(1, counts.getCollectDelta());
        assertEquals(2, counts.getShareCount());
    }
}
