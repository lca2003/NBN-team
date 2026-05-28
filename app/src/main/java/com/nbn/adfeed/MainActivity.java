package com.nbn.adfeed;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;      //增加了intent用于页面跳转

import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.repository.AdRepository;
import com.nbn.adfeed.ui.search.SearchActivity;     //引入搜索界面

public class MainActivity extends Activity {
    private final AdRepository adRepository = new MockAdRepository();
    private final AnalyticsTracker analyticsTracker = new AnalyticsTracker();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        analyticsTracker.trackAppOpen();
        adRepository.getInitialAds();
        //个人分支用于测试搜索页面跳转测试 
        findViewById(R.id.openSearchButton).setOnClickListener(view -> {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivity(intent);
        });
    }
}
