package com.nbn.adfeed.analytics.exposure;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ExposureTrackerTest {

    @Test
    public void waitsUntilAdIsMoreThanHalfVisibleForOneSecond() {
        ExposureTracker tracker = new ExposureTracker(0.5f, 1000L);

        assertTrue(tracker.onVisibilityChanged(ratios("ad_001", 0.50f), 0L).isEmpty());
        assertTrue(tracker.onVisibilityChanged(ratios("ad_001", 0.51f), 0L).isEmpty());
        assertTrue(tracker.onVisibilityChanged(ratios("ad_001", 0.51f), 999L).isEmpty());

        assertEquals(Collections.singletonList("ad_001"),
                tracker.onVisibilityChanged(ratios("ad_001", 0.51f), 1000L));
    }

    @Test
    public void restartsDwellWhenAdDropsBelowVisibleThreshold() {
        ExposureTracker tracker = new ExposureTracker(0.5f, 1000L);

        assertTrue(tracker.onVisibilityChanged(ratios("ad_001", 0.75f), 0L).isEmpty());
        assertTrue(tracker.onVisibilityChanged(ratios("ad_001", 0.49f), 500L).isEmpty());
        assertTrue(tracker.onVisibilityChanged(ratios("ad_001", 0.75f), 1300L).isEmpty());
        assertTrue(tracker.onVisibilityChanged(ratios("ad_001", 0.75f), 2299L).isEmpty());

        assertEquals(Collections.singletonList("ad_001"),
                tracker.onVisibilityChanged(ratios("ad_001", 0.75f), 2300L));
    }

    @Test
    public void exposesSameAdOnlyOncePerTrackerLifecycle() {
        ExposureTracker tracker = new ExposureTracker(0.5f, 1000L);

        tracker.onVisibilityChanged(ratios("ad_001", 0.80f), 0L);
        assertEquals(Collections.singletonList("ad_001"),
                tracker.onVisibilityChanged(ratios("ad_001", 0.80f), 1000L));

        assertTrue(tracker.onVisibilityChanged(ratios("ad_001", 0.80f), 3000L).isEmpty());
    }

    @Test
    public void reportsNextCheckDelayForPendingExposure() {
        ExposureTracker tracker = new ExposureTracker(0.5f, 1000L);

        assertEquals(-1L, tracker.nextCheckDelayMillis(0L));

        tracker.onVisibilityChanged(ratios("ad_001", 0.80f), 100L);

        assertEquals(1000L, tracker.nextCheckDelayMillis(100L));
        assertEquals(400L, tracker.nextCheckDelayMillis(700L));
    }

    private static Map<String, Float> ratios(String adId, float ratio) {
        Map<String, Float> visibleRatios = new LinkedHashMap<>();
        visibleRatios.put(adId, ratio);
        return visibleRatios;
    }
}
