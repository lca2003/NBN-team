package com.nbn.adfeed.ai.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AiSearchResult {
    private final String answer;
    private final List<String> matchedAdIds;
    private final boolean fallback;

    public AiSearchResult(String answer, List<String> matchedAdIds, boolean fallback) {
        this.answer = answer;
        this.matchedAdIds = copyOrEmpty(matchedAdIds);
        this.fallback = fallback;
    }

    public String getAnswer() {
        return answer;
    }

    public List<String> getMatchedAdIds() {
        return matchedAdIds;
    }

    public boolean isFallback() {
        return fallback;
    }

    private static List<String> copyOrEmpty(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}