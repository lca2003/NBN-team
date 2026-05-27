package com.nbn.adfeed.ai;

import com.nbn.adfeed.data.model.AdItem;

import java.util.ArrayList;
import java.util.List;

public final class MockAiSummaryService implements AiSummaryService {
    @Override
    public String summarize(AdItem item) {
        return item.getSummary();
    }

    @Override
    public List<String> generateTags(AdItem item) {
        return new ArrayList<>(item.getTags());
    }
}
