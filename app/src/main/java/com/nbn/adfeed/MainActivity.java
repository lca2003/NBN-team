package com.nbn.adfeed;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.repository.AdRepository;
import com.nbn.adfeed.ui.feed.FeedFragment;
import com.nbn.adfeed.ui.search.SearchBottomSheetDialogFragment;

import java.util.List;

/**
 * 应用入口：承载信息流 {@link FeedFragment}，并提供对话式搜索入口。
 *
 * <p>分工说明：信息流 UI（人员A）封装在 FeedFragment 内；对话式搜索（人员C）通过底部浮层弹出；
 * 数据与统计（人员B/C）通过共享实例注入 Fragment，保证全 App 用同一份数据源与统计口径。</p>
 */
public class MainActivity extends AppCompatActivity
        implements FeedFragment.FeedHost,
        SearchBottomSheetDialogFragment.OnSearchResultListener {

    // 全 App 共享的数据源与统计器：注入给 FeedFragment，保证状态/口径一致。
    private final AdRepository adRepository = new MockAdRepository();
    private final AnalyticsTracker analyticsTracker = new AnalyticsTracker();

    private FeedFragment feedFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        analyticsTracker.trackAppOpen();

        // 首次创建时装载 FeedFragment；旋转重建时复用已有实例，避免状态丢失。
        if (savedInstanceState == null) {
            feedFragment = new FeedFragment();
            feedFragment.configure(adRepository, analyticsTracker);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.feedContainer, feedFragment)
                    .commit();
        } else {
            feedFragment = (FeedFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.feedContainer);
            if (feedFragment != null) {
                feedFragment.configure(adRepository, analyticsTracker);
            }
        }
    }

    /** FeedFragment 顶部搜索入口回调：弹出对话式搜索底部浮层（人员C）。 */
    @Override
    public void onOpenSearch() {
        SearchBottomSheetDialogFragment searchSheet = new SearchBottomSheetDialogFragment();
        searchSheet.show(getSupportFragmentManager(), "SearchBottomSheet");
    }

    /**
     * 接收对话式搜索返回的匹配广告 ID。
     *
     * <p>当前阶段把结果提示交回搜索浮层处理；后续可在此把 matchedAdIds 传给 FeedFragment
     * 做信息流过滤展示（与人员C的标签过滤功能对接）。</p>
     */
    @Override
    public void onSearchResult(List<String> matchedAdIds) {
        // 预留：把搜索结果联动到信息流（例如只展示匹配广告）。
    }
}