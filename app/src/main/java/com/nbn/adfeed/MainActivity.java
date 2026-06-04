package com.nbn.adfeed;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.data.repository.AppRepositoryProvider;
import com.nbn.adfeed.data.repository.AdRepository;
import com.nbn.adfeed.ui.feed.FeedFragment;
import com.nbn.adfeed.ui.navigation.BottomNavSelection;
import com.nbn.adfeed.ui.search.SearchBottomSheetDialogFragment;
import com.nbn.adfeed.ui.stats.StatsActivity;

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
    
    private static final String TAG_FEED = "feed";
    private static final String TAG_SEARCH_BOTTOM_SHEET = "SearchBottomSheet";

    // 全 App 共享的数据源与统计器：注入给 FeedFragment，保证状态/口径一致。
    private final AdRepository adRepository = AppRepositoryProvider.getAdRepository();
    // 统计器需要 Activity Context 创建 SQLiteOpenHelper，因此放到 onCreate 中初始化。
    private AnalyticsTracker analyticsTracker;

    private FeedFragment feedFragment;
    private ImageView navHomeIcon;
    private ImageView navStatsIcon;
    private TextView navHomeText;
    private TextView navStatsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 用带 Context 的构造函数启用曝光/点击事件的 SQLite 持久化。
        analyticsTracker = new AnalyticsTracker(this);
        analyticsTracker.trackAppOpen();
        bindBottomNav();
        restoreOrCreateFeedFragment();
        // 首次创建时装载 FeedFragment；旋转重建时复用已有实例，避免状态丢失。
        showFeed();
    }

    /** 绑定底部三栏导航：首页保持当前页，搜索弹出底部浮层，统计进入独立页面。 */
    private void bindBottomNav() {
        navHomeIcon = findViewById(R.id.navHomeIcon);
        navStatsIcon = findViewById(R.id.navStatsIcon);
        navHomeText = findViewById(R.id.navHomeText);
        navStatsText = findViewById(R.id.navStatsText);

        findViewById(R.id.navHome).setOnClickListener(v -> showFeed());
        findViewById(R.id.navSearch).setOnClickListener(v -> onOpenSearch());
        findViewById(R.id.navStats).setOnClickListener(v -> openStatsActivity());
    }

    /**
     * 复用 FragmentManager 中已恢复的 Fragment，并重新注入共享依赖。
     *
     * <p>即使系统重建了 Fragment 实例，也要重新 configure，避免 Fragment 内部引用旧数据源。</p>
     */
    private void restoreOrCreateFeedFragment() {
        Fragment restoredFeed = getSupportFragmentManager().findFragmentByTag(TAG_FEED);
        if (restoredFeed instanceof FeedFragment) {
            feedFragment = (FeedFragment) restoredFeed;
        } else {
            feedFragment = new FeedFragment();
        }
        feedFragment.configure(adRepository, analyticsTracker);
    }

    /** 切换到首页信息流，并更新底部导航选中态。 */
    private void showFeed() {
        showFragment(feedFragment, TAG_FEED);
        setSelectedNav(true);
    }

    /** 用同一个主容器承载当前页面。 */
    private void showFragment(Fragment fragment, String tag) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.mainContainer, fragment, tag)
                .commit();
    }

    /** 打开独立统计页面。 */
    private void openStatsActivity() {
        startActivity(new Intent(this, StatsActivity.class));
        overridePendingTransition(0, 0);
    }

    /** 当前只高亮首页/统计两个可切换页面；搜索是即时浮层入口，不保持选中态。 */
    private void setSelectedNav(boolean isFeedSelected) {
        BottomNavSelection selection = isFeedSelected
                ? BottomNavSelection.home()
                : BottomNavSelection.stats();
        int homeColor = getColor(selection.getHomeColorResId());
        int statsColor = getColor(selection.getStatsColorResId());
        navHomeIcon.setColorFilter(homeColor);
        navHomeText.setTextColor(homeColor);
        navStatsIcon.setColorFilter(statsColor);
        navStatsText.setTextColor(statsColor);
    }

    /** FeedFragment 顶部搜索入口回调：弹出对话式搜索底部浮层（人员C）。 */
    @Override
    public void onOpenSearch() {
        SearchBottomSheetDialogFragment searchSheet = new SearchBottomSheetDialogFragment();
        searchSheet.show(getSupportFragmentManager(), TAG_SEARCH_BOTTOM_SHEET);
    }

    /**
     * 接收对话式搜索返回的匹配广告 ID。
     *
     * <p>搜索结果交给 FeedFragment 做信息流过滤展示，搜索浮层只负责展示对话提示。</p>
     */
    @Override
    public void onSearchResult(List<String> matchedAdIds) {
        feedFragment.applySearchResult(matchedAdIds);
    }
}
