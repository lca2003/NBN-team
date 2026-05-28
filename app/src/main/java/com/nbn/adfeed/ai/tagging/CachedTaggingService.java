package com.nbn.adfeed.ai.tagging;

import com.nbn.adfeed.ai.AiGenerationException;
import com.nbn.adfeed.ai.AiOutputSource;
import com.nbn.adfeed.ai.cache.AiCacheKey;
import com.nbn.adfeed.ai.cache.AiOutputCache;
import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CachedTaggingService {
    public static final String PROMPT_VERSION = "tagging_v1";
    private static final int MIN_TAG_COUNT = 3;
    private static final int MAX_TAG_COUNT = 5;
    private static final int MAX_TAG_LENGTH = 6;

    private final AiOutputCache cache;
    private final TagGenerator remoteGenerator;

    public CachedTaggingService() {
        this(new AiOutputCache(), null);
    }

    public CachedTaggingService(AiOutputCache cache, TagGenerator remoteGenerator) {
        this.cache = cache;
        this.remoteGenerator = remoteGenerator;
    }

    public TaggingResult generateTags(AdItem item) {
        AiCacheKey key = AiCacheKey.forAd(item, PROMPT_VERSION);
        List<String> cachedTags = cache.getTags(key);
        if (cachedTags != null) {
            return new TaggingResult(item.getId(), cachedTags, AiOutputSource.CACHE, true);
        }

        if (remoteGenerator != null) {
            try {
                List<String> remoteTags = normalize(remoteGenerator.generateTags(item), item);
                if (!remoteTags.isEmpty()) {
                    cache.putTags(key, remoteTags);
                    return new TaggingResult(item.getId(), remoteTags, AiOutputSource.REMOTE_AI, false);
                }
            } catch (AiGenerationException ignored) {
                // Remote failure is expected in offline demo mode; local fallback keeps UI usable.
            }
        }

        return new TaggingResult(item.getId(), normalize(item.getTags(), item), AiOutputSource.LOCAL_FALLBACK, false);
    }

    private static List<String> normalize(List<String> sourceTags, AdItem item) {
        Set<String> uniqueTags = new LinkedHashSet<>();
        addTags(uniqueTags, sourceTags);
        addTag(uniqueTags, item.getChannel());
        addTag(uniqueTags, contentTypeTag(item.getContentType()));
        addTag(uniqueTags, "推荐");
        addTag(uniqueTags, "广告");

        List<String> normalizedTags = new ArrayList<>();
        for (String tag : uniqueTags) {
            normalizedTags.add(tag);
            if (normalizedTags.size() == MAX_TAG_COUNT) {
                break;
            }
        }
        return normalizedTags;
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
}
