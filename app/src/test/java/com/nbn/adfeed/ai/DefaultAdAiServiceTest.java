package com.nbn.adfeed.ai;

import com.nbn.adfeed.data.mock.MockAdRepository;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DefaultAdAiServiceTest {
    @Test
    public void defaultServiceUsesDemoRemoteOnFirstRequest() {
        DefaultAdAiService service = new DefaultAdAiService(new MockAdRepository());

        AiResponse<String> summary = service.getAiSummary("ad_001");
        AiResponse<List<String>> tags = service.getAiTags("ad_001");

        assertEquals(AiOutputSource.REMOTE_AI, summary.getSource());
        assertFalse(summary.isCached());
        assertTrue(summary.getValue().length() <= 40);
        assertEquals(AiOutputSource.REMOTE_AI, tags.getSource());
        assertFalse(tags.isCached());
        assertDemoTags(tags.getValue());
    }

    @Test
    public void defaultServiceCachesSecondRequestInSameInstance() {
        DefaultAdAiService service = new DefaultAdAiService(new MockAdRepository());

        service.getAiSummary("ad_001");
        service.getAiTags("ad_001");
        AiResponse<String> cachedSummary = service.getAiSummary("ad_001");
        AiResponse<List<String>> cachedTags = service.getAiTags("ad_001");

        assertEquals(AiOutputSource.CACHE, cachedSummary.getSource());
        assertTrue(cachedSummary.isCached());
        assertEquals(AiOutputSource.CACHE, cachedTags.getSource());
        assertTrue(cachedTags.isCached());
    }

    @Test
    public void defaultServiceFallsBackForWeakDemoContent() {
        DefaultAdAiService service = new DefaultAdAiService(new MockAdRepository());

        AiResponse<String> summary = service.getAiSummary("ad_030");
        AiResponse<List<String>> tags = service.getAiTags("ad_030");

        assertEquals(AiOutputSource.RULE_FALLBACK, summary.getSource());
        assertTrue(summary.getValue().length() <= 40);
        assertEquals(AiOutputSource.MOCK_FALLBACK, tags.getSource());
        assertDemoTags(tags.getValue());
    }

    private static void assertDemoTags(List<String> tags) {
        assertTrue(tags.size() >= 3);
        assertTrue(tags.size() <= 5);
        for (String tag : tags) {
            assertTrue(tag.length() <= 6);
        }
    }
}
