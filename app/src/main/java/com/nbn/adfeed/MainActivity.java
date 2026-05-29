package com.nbn.adfeed;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.repository.AdRepository;
import com.nbn.adfeed.ui.search.SearchBottomSheetDialogFragment;

import java.util.List;

public class MainActivity extends AppCompatActivity
        implements SearchBottomSheetDialogFragment.OnSearchResultListener {
    private final AdRepository adRepository = new MockAdRepository();
    private final AnalyticsTracker analyticsTracker = new AnalyticsTracker();
    private TextView feedStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        analyticsTracker.trackAppOpen();
        adRepository.getInitialAds();
        feedStatusText = findViewById(R.id.feedStatusText);
        findViewById(R.id.openSearchButton).setOnClickListener(view -> {
            // 页面跳转逻辑修改，改成在本页面弹出底部浮层
            SearchBottomSheetDialogFragment searchSheet = new SearchBottomSheetDialogFragment();
            searchSheet.show(getSupportFragmentManager(), "SearchBottomSheet");
        });
    }
    
    // TODO 负责显示聊天搜索得到的结果
    @Override
    public void onSearchResult(List<String> matchedAdIds) {
        feedStatusText.setText(getString(R.string.home_search_result_prefix) + String.join(", ", matchedAdIds));
    }
}
