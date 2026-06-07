package com.nbn.adfeed.ui.detail;

import android.content.Context;
import android.content.Intent;

import com.nbn.adfeed.R;
import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.analytics.event.AdAnalyticsEventCounts;
import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionState;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class AdDetailActivityPersistenceTest {
    private static final String DATABASE_NAME = "ad_analytics_events.db";

    private final Context context = RuntimeEnvironment.getApplication();

    @After
    public void tearDown() {
        context.deleteDatabase(DATABASE_NAME);
    }

    @Test
    public void likeClickPersistsAsAnalyticsEvent() {
        AdItem ad = ad("detail_like_persist_001");
        Intent intent = AdDetailActivity.newIntent(context, ad);
        AdDetailActivity activity = Robolectric.buildActivity(AdDetailActivity.class, intent)
                .setup()
                .get();

        activity.findViewById(R.id.likeContainer).performClick();

        Map<String, AdAnalyticsEventCounts> countsByAdId =
                new AnalyticsTracker(context).loadCountsByAdId();
        AdAnalyticsEventCounts counts = countsByAdId.get(ad.getId());

        assertEquals(1, counts.getLikeDelta());
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
}
