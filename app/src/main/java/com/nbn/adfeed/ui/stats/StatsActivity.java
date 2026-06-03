package com.nbn.adfeed.ui.stats;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.nbn.adfeed.R;
import com.nbn.adfeed.data.repository.AppRepositoryProvider;
import com.nbn.adfeed.data.repository.AdRepository;
import com.nbn.adfeed.ui.navigation.BottomNavSelection;
import com.nbn.adfeed.ui.search.SearchBottomSheetDialogFragment;

import java.util.List;

/**
 * 独立统计页面：承载统计 Fragment，并复用首页底部导航。
 */
public final class StatsActivity extends AppCompatActivity
        implements SearchBottomSheetDialogFragment.OnSearchResultListener {
    private static final String TAG_STATS = "stats";
    private static final String TAG_SEARCH_BOTTOM_SHEET = "SearchBottomSheet";

    private final AdRepository adRepository = AppRepositoryProvider.getAdRepository();

    private StatsFragment statsFragment;
    private ImageView navHomeIcon;
    private ImageView navStatsIcon;
    private TextView navHomeText;
    private TextView navStatsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        bindBottomNav();
        restoreOrCreateStatsFragment();
        showStats();
    }

    /** 首页返回主 Activity，搜索弹出浮层，统计保持当前页。 */
    private void bindBottomNav() {
        navHomeIcon = findViewById(R.id.navHomeIcon);
        navStatsIcon = findViewById(R.id.navStatsIcon);
        navHomeText = findViewById(R.id.navHomeText);
        navStatsText = findViewById(R.id.navStatsText);

        findViewById(R.id.navHome).setOnClickListener(v -> closeToHome());
        findViewById(R.id.navSearch).setOnClickListener(v -> onOpenSearch());
        findViewById(R.id.navStats).setOnClickListener(v -> showStats());
    }

    private void restoreOrCreateStatsFragment() {
        Fragment restoredStats = getSupportFragmentManager().findFragmentByTag(TAG_STATS);
        if (restoredStats instanceof StatsFragment) {
            statsFragment = (StatsFragment) restoredStats;
        } else {
            statsFragment = new StatsFragment();
        }
        statsFragment.configure(adRepository);
    }

    private void showStats() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.statsContainer, statsFragment, TAG_STATS)
                .commit();
        setSelectedNav(false);
    }

    private void onOpenSearch() {
        SearchBottomSheetDialogFragment searchSheet = new SearchBottomSheetDialogFragment();
        searchSheet.show(getSupportFragmentManager(), TAG_SEARCH_BOTTOM_SHEET);
    }

    private void closeToHome() {
        finish();
        overridePendingTransition(0, 0);
    }

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

    @Override
    public void onSearchResult(List<String> matchedAdIds) {
        // Reserved for future feed filtering.
    }
}
