package com.nbn.adfeed.ai.tagging;

import com.nbn.adfeed.ai.AiOutputSource;
import com.nbn.adfeed.ai.cache.AiOutputCache;
import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.model.AdItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CachedTaggingServiceTest {
    private final AdItem ad = new MockAdRepository().getAdById("ad_001");

    @Test
    public void generateTagsNormalizesRemoteOutputThenCaches() {
        AtomicInteger calls = new AtomicInteger();
        CachedTaggingService service = new CachedTaggingService(
                new AiOutputCache(),
                item -> {
                    calls.incrementAndGet();
                    return Arrays.asList("超长标签名称会被截断", "运动", "运动", "学生党", "通勤", "额外");
                }
        );

        TaggingResult first = service.generateTags(ad);
        TaggingResult second = service.generateTags(ad);

        assertEquals(AiOutputSource.REMOTE_AI, first.getSource());
        assertFalse(first.isCached());
        assertTrue(first.getTags().size() <= 5);
        assertEquals(new HashSet<>(first.getTags()).size(), first.getTags().size());
        for (String tag : first.getTags()) {
            assertTrue(tag.length() <= 6);
        }
        assertEquals(AiOutputSource.CACHE, second.getSource());
        assertTrue(second.isCached());
        assertEquals(1, calls.get());
    }

    @Test
    public void generateTagsFallsBackToAdTags() {
        CachedTaggingService service = new CachedTaggingService(new AiOutputCache(), null);

        TaggingResult result = service.generateTags(ad);

        assertEquals(AiOutputSource.LOCAL_FALLBACK, result.getSource());
        assertTrue(result.getTags().contains("运动"));
        assertTrue(result.getTags().contains("学生党"));
    }
}
