package com.nbn.adfeed.ai.tagging;

import com.nbn.adfeed.ai.AiGenerationException;
import com.nbn.adfeed.ai.AiOutputSource;
import com.nbn.adfeed.ai.AiResponse;
import com.nbn.adfeed.ai.AiTaggingService;
import com.nbn.adfeed.ai.cache.AiCache;
import com.nbn.adfeed.ai.cache.AiCacheKey;
import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CachedTaggingService implements AiTaggingService {
    public static final String PROMPT_VERSION = "tagging_v1";
    private static final int MAX_TAG_COUNT = 5;
    private static final int MAX_TAG_LENGTH = 6;

    private final AiCache cache;
    private final TagGenerator remoteGenerator;

    public CachedTaggingService() {
        this(new AiCache(), null);
    }

    public CachedTaggingService(AiCache cache, TagGenerator remoteGenerator) {
        this.cache = cache == null ? new AiCache() : cache;
        this.remoteGenerator = remoteGenerator;
    }

    @Override
    public AiResponse<List<String>> generateTags(AdItem item) {
        AiCacheKey key = AiCacheKey.forAd(item, PROMPT_VERSION);
        List<String> cachedTags = cache.getTags(key);
        if (cachedTags != null) {
            return AiResponse.success(cachedTags, AiOutputSource.CACHE, true);
        }

        if (remoteGenerator != null) {
            try {
                List<String> remoteTags = normalize(remoteGenerator.generateTags(item), item);
                if (!remoteTags.isEmpty()) {
                    cache.putTags(key, remoteTags);
                    return AiResponse.success(remoteTags, AiOutputSource.REMOTE_AI, false);
                }
            } catch (AiGenerationException ignored) {
                // Offline demo mode falls through to deterministic local output.
            }
        }

        if (item.getTags() != null && !item.getTags().isEmpty()) {
            List<String> mockTags = normalize(item.getTags(), item);
            cache.putTags(key, mockTags);
            return AiResponse.failure(mockTags, AiOutputSource.MOCK_FALLBACK, "Use mock tags", null);
        }

        List<String> ruleTags = ruleTags(item);
        cache.putTags(key, ruleTags);
        return AiResponse.failure(ruleTags, AiOutputSource.RULE_FALLBACK, "Use rule fallback tags", null);
    }

    private static List<String> normalize(List<String> sourceTags, AdItem item) {
        Set<String> uniqueTags = new LinkedHashSet<>();
        addTags(uniqueTags, sourceTags);
        addTag(uniqueTags, item.getChannel());
        addTag(uniqueTags, contentTypeTag(item.getContentType()));
        addTag(uniqueTags, inferAudience(item));
        addTag(uniqueTags, "推荐");

        List<String> normalizedTags = new ArrayList<>();
        for (String tag : uniqueTags) {
            normalizedTags.add(tag);
            if (normalizedTags.size() == MAX_TAG_COUNT) {
                break;
            }
        }
        return normalizedTags;
    }

    private static List<String> ruleTags(AdItem item) {
        List<String> tags = new ArrayList<>();
        tags.add(item.getChannel());
        tags.add(contentTypeTag(item.getContentType()));
        tags.add(inferAudience(item));
        return normalize(tags, item);
    }

    private static void addTags(Set<String> target, List<String> sourceTags) {
        if (sourceTags == null) {
            return;
        }
        for (String tag : sourceTags) {
            addTag(target, tag);
        }
    }

    private static void addTag(Set<String> target, String tag) {
        if (target.size() >= MAX_TAG_COUNT || tag == null) {
            return;
        }
        String normalized = tag.trim();
        if (normalized.isEmpty()) {
            return;
        }
        if (normalized.length() > MAX_TAG_LENGTH) {
            normalized = normalized.substring(0, MAX_TAG_LENGTH);
        }
        target.add(normalized);
    }

    private static String contentTypeTag(AdContentType contentType) {
        if (contentType == AdContentType.VIDEO) {
            return "视频";
        }
        return "图文";
    }

    private static String inferAudience(AdItem item) {
        String text = item.getTitle() + item.getDescription() + item.getSummary();
        if (text.contains("学生") || text.contains("校园") || text.contains("宿舍")) {
            return "学生党";
        }
        if (text.contains("通勤") || text.contains("上班")) {
            return "通勤";
        }
        if (text.contains("咖啡")) {
            return "咖啡";
        }
        if (text.contains("运动") || text.contains("健身")) {
            return "运动";
        }
        return "生活";
    }
}
