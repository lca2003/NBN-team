package com.nbn.adfeed;

import android.app.Activity;
import android.os.Bundle;

import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.repository.AdRepository;

public class MainActivity extends Activity {
    private final AdRepository adRepository = new MockAdRepository();
    private final AnalyticsTracker analyticsTracker = new AnalyticsTracker();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        analyticsTracker.trackAppOpen();
        adRepository.getInitialAds();
    }
}
