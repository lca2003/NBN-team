package com.nbn.adfeed.ai;

import com.nbn.adfeed.ai.cache.AiCache;
import com.nbn.adfeed.ai.summary.CachedSummaryService;
import com.nbn.adfeed.ai.tagging.CachedTaggingService;
import com.nbn.adfeed.data.model.AdItem;

import java.util.List;

public final class MockAiSummaryService implements AiSummaryService, AiTaggingService {
    private final CachedSummaryService summaryService;
    private final CachedTaggingService taggingService;

    public MockAiSummaryService() {
        AiCache cache = new AiCache();
        summaryService = new CachedSummaryService(cache, null);
        taggingService = new CachedTaggingService(cache, null);
    }

    @Override
    public AiResponse<String> summarize(AdItem item) {
        return summaryService.summarize(item);
    }

    @Override
    public AiResponse<List<String>> generateTags(AdItem item) {
        return taggingService.generateTags(item);
    }
}
