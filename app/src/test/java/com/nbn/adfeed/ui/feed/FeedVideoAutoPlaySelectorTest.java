package com.nbn.adfeed.ui.feed;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public final class FeedVideoAutoPlaySelectorTest {
    @Test
    public void selectsOnlyTheMostVisiblePlayableVideo() {
        Map<Integer, Float> visibleRatios = new LinkedHashMap<>();
        visibleRatios.put(2, 0.72f);
        visibleRatios.put(5, 0.91f);
        visibleRatios.put(8, 0.68f);

        assertEquals(5, FeedVideoAutoPlaySelector.selectPosition(visibleRatios));
    }

    @Test
    public void ignoresVideosBelowAutoPlayVisibilityThreshold() {
        Map<Integer, Float> visibleRatios = new LinkedHashMap<>();
        visibleRatios.put(2, 0.59f);
        visibleRatios.put(5, 0.40f);

        assertEquals(
                RecyclerView.NO_POSITION,
                FeedVideoAutoPlaySelector.selectPosition(visibleRatios)
        );
    }
}
