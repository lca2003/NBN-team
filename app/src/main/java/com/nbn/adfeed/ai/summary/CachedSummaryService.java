package com.nbn.adfeed.ai.summary;

import com.nbn.adfeed.ai.AiGenerationException;
import com.nbn.adfeed.ai.AiOutputSource;
import com.nbn.adfeed.ai.AiResponse;
import com.nbn.adfeed.ai.AiSummaryService;
import com.nbn.adfeed.ai.cache.AiCache;
import com.nbn.adfeed.ai.cache.AiCacheKey;
import com.nbn.adfeed.data.model.AdItem;

public final class CachedSummaryService implements AiSummaryService {
    public static final String PROMPT_VERSION = "summary_v1";
    private static final int MAX_SUMMARY_LENGTH = 40;

    private final AiCache cache;
    private final SummaryGenerator remoteGenerator;

    public CachedSummaryService() {
        this(new AiCache(), null);
    }

    public CachedSummaryService(AiCache cache, SummaryGenerator remoteGenerator) {
        this.cache = cache == null ? new AiCache() : cache;
        this.remoteGenerator = remoteGenerator;
    }

    @Override
    public AiResponse<String> summarize(AdItem item) {
        AiCacheKey key = AiCacheKey.forAd(item, PROMPT_VERSION);
        String cachedSummary = cache.getSummary(key);
        if (cachedSummary != null) {
            return AiResponse.success(cachedSummary, AiOutputSource.CACHE, true);
        }

        if (remoteGenerator != null) {
            try {
                String remoteSummary = normalize(remoteGenerator.generateSummary(item));
                if (!remoteSummary.isEmpty()) {
                    cache.putSummary(key, remoteSummary);
                    return AiResponse.success(remoteSummary, AiOutputSource.REMOTE_AI, false);
                }
            } catch (AiGenerationException ignored) {
                // Offline demo mode falls through to deterministic local output.
            }
        }

        String mockSummary = normalize(item.getSummary());
        if (!mockSummary.isEmpty()) {
            cache.putSummary(key, mockSummary);
            return AiResponse.failure(mockSummary, AiOutputSource.MOCK_FALLBACK, "Use mock summary", null);
        }

        String ruleSummary = fallbackSummary(item);
        cache.putSummary(key, ruleSummary);
        return AiResponse.failure(ruleSummary, AiOutputSource.RULE_FALLBACK, "Use rule fallback summary", null);
    }

    private static String fallbackSummary(AdItem item) {
        String source = firstNonBlank(item.getDescription(), item.getTitle(), item.getBrand(), item.getChannel());
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
