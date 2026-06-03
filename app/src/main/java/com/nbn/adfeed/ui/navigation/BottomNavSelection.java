package com.nbn.adfeed.ui.navigation;

import com.nbn.adfeed.R;

/**
 * 底部导航选中态颜色模型，统一文字和图标高亮口径。
 */
public final class BottomNavSelection {
    private final int homeColorResId;
    private final int statsColorResId;

    private BottomNavSelection(int homeColorResId, int statsColorResId) {
        this.homeColorResId = homeColorResId;
        this.statsColorResId = statsColorResId;
    }

    public static BottomNavSelection home() {
        return new BottomNavSelection(R.color.nbn_text_primary, R.color.nbn_text_secondary);
    }

    public static BottomNavSelection stats() {
        return new BottomNavSelection(R.color.nbn_text_secondary, R.color.nbn_text_primary);
    }

    public int getHomeColorResId() {
        return homeColorResId;
    }

    public int getStatsColorResId() {
        return statsColorResId;
    }
}
