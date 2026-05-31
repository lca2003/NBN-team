package com.nbn.adfeed.ai.summary;

import com.nbn.adfeed.ai.AiGenerationException;
import com.nbn.adfeed.ai.AiOutputSource;
import com.nbn.adfeed.ai.AiResponse;
import com.nbn.adfeed.ai.cache.AiCache;
import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.model.AdItem;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CachedSummaryServiceTest {
    private final AdItem ad = new MockAdRepository().getAdById("ad_001").getData();

    @Test
    public void summarizeUsesRemoteOnceThenCache() {
        AtomicInteger calls = new AtomicInteger();
        CachedSummaryService service = new CachedSummaryService(
                new AiCache(),
                item -> {
                    calls.incrementAndGet();
                    return "远程摘要结果";
                }
        );

        AiResponse<String> first = service.summarize(ad);
        AiResponse<String> second = service.summarize(ad);

        assertEquals("远程摘要结果", first.getValue());
        assertEquals(AiOutputSource.REMOTE_AI, first.getSource());
        assertFalse(first.isCached());
        assertEquals(AiOutputSource.CACHE, second.getSource());
        assertTrue(second.isCached());
        assertEquals(1, calls.get());
    }

    @Test
    public void summarizeFallsBackWhenRemoteFails() {
        CachedSummaryService service = new CachedSummaryService(
                new AiCache(),
                item -> {
                    throw new AiGenerationException("timeout");
                }
        );

        AiResponse<String> result = service.summarize(ad);

        assertEquals(AiOutputSource.MOCK_FALLBACK, result.getSource());
        assertTrue(result.getValue().length() <= 40);
    }

    @Test
    public void summarizeUsesRuleFallbackWhenMockSummaryIsMissing() {
        CachedSummaryService service = new CachedSummaryService(new AiCache(), null);

        AiResponse<String> result = service.summarize(ad.withSummary(""));

        assertEquals(AiOutputSource.RULE_FALLBACK, result.getSource());
        assertTrue(result.getValue().length() <= 40);
    }

    @Test
    public void contentChangeInvalidatesCache() {
        AtomicInteger calls = new AtomicInteger();
        CachedSummaryService service = new CachedSummaryService(new AiCache(), item -> "摘要" + calls.incrementAndGet());

        AiResponse<String> first = service.summarize(ad);
        AiResponse<String> changed = service.summarize(ad.withSummary("新的摘要内容"));

        assertEquals("摘要1", first.getValue());
        assertEquals("摘要2", changed.getValue());
    }
}
