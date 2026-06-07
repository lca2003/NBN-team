package com.nbn.adfeed.ai;

import com.nbn.adfeed.ai.cache.AiCache;
import com.nbn.adfeed.ai.summary.CachedSummaryService;
import com.nbn.adfeed.ai.tagging.CachedTaggingService;
import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.model.AdItem;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class AiQualityTest {
    @Test
    public void aiSummaryAndTagsMeetDemoConstraints() {
        MockAdRepository repository = new MockAdRepository();
        AiCache cache = new AiCache();
        CachedSummaryService summaryService = new CachedSummaryService(cache, null);
        CachedTaggingService taggingService = new CachedTaggingService(cache, null);

        for (AdItem ad : repository.snapshot()) {
            AiResponse<String> summary = summaryService.summarize(ad);
            AiResponse<List<String>> tags = taggingService.generateTags(ad);

            assertTrue(summary.getValue().length() <= 40);
            assertTrue(tags.getValue().size() >= 3);
            assertTrue(tags.getValue().size() <= 5);
            for (String tag : tags.getValue()) {
                assertTrue(tag.length() <= 6);
            }
        }
    }

    @Test
    public void secondRequestHitsCache() {
        AdItem ad = new MockAdRepository().getAdById("ad_030").getData();
        AiCache cache = new AiCache();
        CachedSummaryService summaryService = new CachedSummaryService(cache, null);

        summaryService.summarize(ad);
        AiResponse<String> second = summaryService.summarize(ad);

        assertEquals(AiOutputSource.CACHE, second.getSource());
        assertTrue(second.isCached());
    }
}
