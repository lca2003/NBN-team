package com.nbn.adfeed.ai.summary;

import com.nbn.adfeed.ai.AiOutputSource;

public final class AiSummaryResult {
    private final String adId;
    private final String summary;
    private final AiOutputSource source;
    private final boolean cached;

    public AiSummaryResult(String adId, String summary, AiOutputSource source, boolean cached) {
        this.adId = adId;
        this.summary = summary;
        this.source = source;
        this.cached = cached;
    }

    public String getAdId() {
        return adId;
    }

    public String getSummary() {
        return summary;
    }

    public AiOutputSource getSource() {
        return source;
    }

    public boolean isCached() {
        return cached;
    }
}
