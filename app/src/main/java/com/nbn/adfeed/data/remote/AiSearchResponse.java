package com.nbn.adfeed.data.remote;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AiSearchResponse {
    @SerializedName("answer")
    private String answer;

    @SerializedName("matchedAdIds")
    private List<String> matchedAdIds;

    @SerializedName("fallback")
    private boolean fallback;

    public AiSearchResponse(String answer, List<String> matchedAdIds, boolean fallback) {
        this.answer = answer;
        this.matchedAdIds = copyOrEmpty(matchedAdIds);
        this.fallback = fallback;
    }

    private AiSearchResponse() {
        matchedAdIds = Collections.emptyList();
    }

    public String getAnswer() {
        return answer;
    }

    public List<String> getMatchedAdIds() {
        return Collections.unmodifiableList(matchedAdIds == null ? Collections.emptyList() : matchedAdIds);
    }

    public boolean isFallback() {
        return fallback;
    }

    private static List<String> copyOrEmpty(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(values);
    }
}
