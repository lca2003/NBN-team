package com.nbn.adfeed.ui.navigation;

import static org.junit.Assert.assertEquals;

import com.nbn.adfeed.R;

import org.junit.Test;

public final class BottomNavSelectionTest {
    @Test
    public void selectedStats_usesPrimaryColorForStatsAndSecondaryForHome() {
        BottomNavSelection selection = BottomNavSelection.stats();

        assertEquals(R.color.nbn_text_secondary, selection.getHomeColorResId());
        assertEquals(R.color.nbn_text_primary, selection.getStatsColorResId());
    }

    @Test
    public void selectedHome_usesPrimaryColorForHomeAndSecondaryForStats() {
        BottomNavSelection selection = BottomNavSelection.home();

        assertEquals(R.color.nbn_text_primary, selection.getHomeColorResId());
        assertEquals(R.color.nbn_text_secondary, selection.getStatsColorResId());
    }
}
