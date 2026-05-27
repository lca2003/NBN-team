package com.nbn.adfeed.analytics;

import java.util.HashMap;
import java.util.Map;

public final class AnalyticsTracker {
    private final Map<String, Integer> counters = new HashMap<>();

    public void trackAppOpen() {
        increase("app_open");
    }

    public void trackExposure(String adId) {
        increase("exposure_" + adId);
    }

    public void trackClick(String adId) {
        increase("click_" + adId);
    }

    public Map<String, Integer> snapshot() {
        return new HashMap<>(counters);
    }

    private void increase(String key) {
        Integer current = counters.get(key);
        counters.put(key, current == null ? 1 : current + 1);
    }
}
