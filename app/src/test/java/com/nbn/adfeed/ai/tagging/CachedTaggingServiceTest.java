package com.nbn.adfeed.ai.tagging;

import com.nbn.adfeed.ai.AiOutputSource;
import com.nbn.adfeed.ai.AiResponse;
import com.nbn.adfeed.ai.cache.AiCache;
import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.model.AdItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CachedTaggingServiceTest {
    private final AdItem ad = new MockAdRepository().getAdById("ad_001").getData();

    @Test
    public void generateTagsNormalizesRemoteOutputThenCaches() {
        AtomicInteger calls = new AtomicInteger();
        CachedTaggingService service = new CachedTaggingService(
                new AiCache(),
                item -> {
                    calls.incrementAndGet();
                    return Arrays.asList("超长标签名称会被截断", "运动", "运动", "学生党", "通勤", "额外");
                }
        );

        AiResponse<List<String>> first = service.generateTags(ad);
        AiResponse<List<String>> second = service.generateTags(ad);

        assertEquals(AiOutputSource.REMOTE_AI, first.getSource());
        assertFalse(first.isCached());
        assertTrue(first.getValue().size() >= 3);
        assertTrue(first.getValue().size() <= 5);
        assertEquals(new HashSet<>(first.getValue()).size(), first.getValue().size());
        for (String tag : first.getValue()) {
            assertTrue(tag.length() <= 6);
        }
        assertEquals(AiOutputSource.CACHE, second.getSource());
        assertTrue(second.isCached());
        assertEquals(1, calls.get());
    }

    @Test
    public void generateTagsFallsBackToAdTags() {
        CachedTaggingService service = new CachedTaggingService(new AiCache(), null);

        AiResponse<List<String>> result = service.generateTags(ad);

        assertEquals(AiOutputSource.MOCK_FALLBACK, result.getSource());
        assertTrue(result.getValue().contains("运动"));
        assertTrue(result.getValue().contains("学生党"));
    }

    @Test
    public void generateTagsUsesRuleFallbackWhenMockTagsAreMissing() {
        CachedTaggingService service = new CachedTaggingService(new AiCache(), null);

        AiResponse<List<String>> result = service.generateTags(ad.withTags(Collections.emptyList()));

        assertEquals(AiOutputSource.RULE_FALLBACK, result.getSource());
        assertTrue(result.getValue().size() >= 3);
        assertTrue(result.getValue().size() <= 5);
    }
}
