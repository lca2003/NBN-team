package com.nbn.adfeed.ai.tagging;

import com.nbn.adfeed.ai.AiOutputSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TaggingResult {
    private final String adId;
    private final List<String> tags;
    private final AiOutputSource source;
    private final boolean cached;

    public TaggingResult(String adId, List<String> tags, AiOutputSource source, boolean cached) {
        this.adId = adId;
        this.tags = Collections.unmodifiableList(new ArrayList<>(tags));
        this.source = source;
        this.cached = cached;
    }

    public String getAdId() {
        return adId;
    }

    public List<String> getTags() {
        return tags;
    }

    public AiOutputSource getSource() {
        return source;
    }

    public boolean isCached() {
        return cached;
    }
}
