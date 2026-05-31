package com.nbn.adfeed.ai.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiCache {
    private final Map<AiCacheKey, String> summaries = new HashMap<>();
    private final Map<AiCacheKey, List<String>> tags = new HashMap<>();

    public synchronized String getSummary(AiCacheKey key) {
        return summaries.get(key);
    }

    public synchronized void putSummary(AiCacheKey key, String summary) {
        summaries.put(key, summary);
    }

    public synchronized List<String> getTags(AiCacheKey key) {
        List<String> cachedTags = tags.get(key);
        return cachedTags == null ? null : new ArrayList<>(cachedTags);
    }

    public synchronized void putTags(AiCacheKey key, List<String> value) {
        tags.put(key, new ArrayList<>(value));
    }
}
