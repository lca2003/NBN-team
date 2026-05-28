package com.nbn.adfeed.ai.summary;

import com.nbn.adfeed.ai.AiGenerationException;
import com.nbn.adfeed.ai.AiOutputSource;
import com.nbn.adfeed.ai.cache.AiCacheKey;
import com.nbn.adfeed.ai.cache.AiOutputCache;
import com.nbn.adfeed.data.model.AdItem;

public final class CachedSummaryService {
    public static final String PROMPT_VERSION = "summary_v1";
    private static final int MAX_SUMMARY_LENGTH = 40;

    private final AiOutputCache cache;
    private final SummaryGenerator remoteGenerator;

    public CachedSummaryService() {
        this(new AiOutputCache(), null);
    }

    public CachedSummaryService(AiOutputCache cache, SummaryGenerator remoteGenerator) {
        this.cache = cache;
        this.remoteGenerator = remoteGenerator;
    }

    public AiSummaryResult summarize(AdItem item) {
        AiCacheKey key = AiCacheKey.forAd(item, PROMPT_VERSION);
        String cachedSummary = cache.getSummary(key);
        if (cachedSummary != null) {
            return new AiSummaryResult(item.getId(), cachedSummary, AiOutputSource.CACHE, true);
        }

        if (remoteGenerator != null) {
            try {
                String remoteSummary = normalize(remoteGenerator.generateSummary(item));
                if (!remoteSummary.isEmpty()) {
                    cache.putSummary(key, remoteSummary);
                    return new AiSummaryResult(item.getId(), remoteSummary, AiOutputSource.REMOTE_AI, false);
                }
            } catch (AiGenerationException ignored) {
                // Remote failure is expected in offline demo mode; local fallback keeps UI usable.
            }
        }

        return new AiSummaryResult(item.getId(), fallbackSummary(item), AiOutputSource.LOCAL_FALLBACK, false);
    }

    private static String fallbackSummary(AdItem item) {
        String source = firstNonBlank(item.getSummary(), item.getDescription(), item.getTitle());
        return normalize(source);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "广告信息已准备好，可进入详情查看。";
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\n', ' ').trim();
        if (normalized.length() <= MAX_SUMMARY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SUMMARY_LENGTH);
    }
}
