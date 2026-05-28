package com.nbn.adfeed.ai;

import com.nbn.adfeed.ai.cache.AiOutputCache;
import com.nbn.adfeed.ai.summary.CachedSummaryService;
import com.nbn.adfeed.ai.tagging.CachedTaggingService;
import com.nbn.adfeed.data.model.AdItem;

import java.util.List;

public final class MockAiSummaryService implements AiSummaryService {
    private final CachedSummaryService summaryService;
    private final CachedTaggingService taggingService;

    public MockAiSummaryService() {
        AiOutputCache cache = new AiOutputCache();
        summaryService = new CachedSummaryService(cache, null);
        taggingService = new CachedTaggingService(cache, null);
    }

    @Override
    public String summarize(AdItem item) {
        return summaryService.summarize(item).getSummary();
    }

    @Override
    public List<String> generateTags(AdItem item) {
        return taggingService.generateTags(item).getTags();
    }
}
