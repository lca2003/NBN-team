package com.nbn.adfeed.ai.summary;

import com.nbn.adfeed.ai.AiGenerationException;
import com.nbn.adfeed.ai.AiOutputSource;
import com.nbn.adfeed.ai.cache.AiOutputCache;
import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.model.AdItem;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CachedSummaryServiceTest {
    private final AdItem ad = new MockAdRepository().getAdById("ad_001");

    @Test
    public void summarizeUsesRemoteOnceThenCache() {
        AtomicInteger calls = new AtomicInteger();
        CachedSummaryService service = new CachedSummaryService(
                new AiOutputCache(),
                item -> {
                    calls.incrementAndGet();
                    return "远程摘要结果";
                }
        );

        AiSummaryResult first = service.summarize(ad);
        AiSummaryResult second = service.summarize(ad);

        assertEquals("远程摘要结果", first.getSummary());
        assertEquals(AiOutputSource.REMOTE_AI, first.getSource());
        assertFalse(first.isCached());
        assertEquals(AiOutputSource.CACHE, second.getSource());
        assertTrue(second.isCached());
        assertEquals(1, calls.get());
    }

    @Test
    public void summarizeFallsBackWhenRemoteFails() {
        CachedSummaryService service = new CachedSummaryService(
                new AiOutputCache(),
                item -> {
                    throw new AiGenerationException("timeout");
                }
        );

        AiSummaryResult result = service.summarize(ad);

        assertEquals(AiOutputSource.LOCAL_FALLBACK, result.getSource());
        assertTrue(result.getSummary().length() <= 40);
    }
}
