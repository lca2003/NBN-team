package com.nbn.adfeed.ui.stats;

import android.os.Handler;
import android.os.Looper;

import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.analytics.event.AdAnalyticsEventCounts;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.repository.AdRepository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class StatsDataLoader {
    interface Callback {
        void onLoaded(List<AdItem> ads, Map<String, AdAnalyticsEventCounts> eventCounts);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    void load(AdRepository repository, AnalyticsTracker tracker, Callback callback) {
        executor.execute(() -> {
            List<AdItem> ads;
            Map<String, AdAnalyticsEventCounts> eventCounts;
            try {
                ads = repository == null ? Collections.emptyList() : repository.getAllAdsForStats();
                eventCounts = tracker == null ? Collections.emptyMap() : tracker.loadCountsByAdId();
            } catch (RuntimeException ignored) {
                ads = Collections.emptyList();
                eventCounts = Collections.emptyMap();
            }

            List<AdItem> loadedAds = ads;
            Map<String, AdAnalyticsEventCounts> loadedEventCounts = eventCounts;
            mainHandler.post(() -> callback.onLoaded(loadedAds, loadedEventCounts));
        });
    }

    void close() {
        executor.shutdownNow();
    }
}
