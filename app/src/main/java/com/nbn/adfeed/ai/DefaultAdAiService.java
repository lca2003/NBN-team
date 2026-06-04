package com.nbn.adfeed.ai;

import com.nbn.adfeed.ai.cache.AiCache;
import com.nbn.adfeed.ai.demo.DemoRemoteSummaryGenerator;
import com.nbn.adfeed.ai.demo.DemoRemoteTagGenerator;
import com.nbn.adfeed.ai.summary.CachedSummaryService;
import com.nbn.adfeed.ai.tagging.CachedTaggingService;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.repository.AdRepository;

import java.util.Collections;
import java.util.List;

public final class DefaultAdAiService implements AdAiService {
    private final AdRepository repository;
    private final AiSummaryService summaryService;
    private final AiTaggingService taggingService;

    public DefaultAdAiService(AdRepository repository) {
        AiCache cache = new AiCache();
        this.repository = repository;
        this.summaryService = new CachedSummaryService(cache, new DemoRemoteSummaryGenerator());
        this.taggingService = new CachedTaggingService(cache, new DemoRemoteTagGenerator());
    }

    public DefaultAdAiService(
            AdRepository repository,
            AiSummaryService summaryService,
            AiTaggingService taggingService
    ) {
        this.repository = repository;
        this.summaryService = summaryService;
        this.taggingService = taggingService;
    }

    @Override
    public AiResponse<String> getAiSummary(String adId) {
        DataResult<AdItem> result = repository.getAdById(adId);
        if (!result.hasData()) {
            return AiResponse.failure("", AiOutputSource.RULE_FALLBACK, result.getMessage(), result.getError());
        }
        return summaryService.summarize(result.getData());
    }

    @Override
    public AiResponse<List<String>> getAiTags(String adId) {
        DataResult<AdItem> result = repository.getAdById(adId);
        if (!result.hasData()) {
            return AiResponse.failure(Collections.emptyList(), AiOutputSource.RULE_FALLBACK,
                    result.getMessage(), result.getError());
        }
        return taggingService.generateTags(result.getData());
    }
}
