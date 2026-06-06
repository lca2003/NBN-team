package com.nbn.adfeed.ui.feed;

import androidx.recyclerview.widget.RecyclerView;

import java.util.Map;

final class FeedVideoAutoPlaySelector {
    static final float MIN_VISIBLE_RATIO = 0.60f;

    private FeedVideoAutoPlaySelector() {
    }

    static int selectPosition(Map<Integer, Float> visibleRatios) {
        int selectedPosition = RecyclerView.NO_POSITION;
        float selectedRatio = MIN_VISIBLE_RATIO;
        if (visibleRatios == null) {
            return selectedPosition;
        }

        for (Map.Entry<Integer, Float> entry : visibleRatios.entrySet()) {
            Float ratio = entry.getValue();
            if (ratio != null && ratio >= selectedRatio) {
                selectedPosition = entry.getKey();
                selectedRatio = ratio;
            }
        }
        return selectedPosition;
    }
}
