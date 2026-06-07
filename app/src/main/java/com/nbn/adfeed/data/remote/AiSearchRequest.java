package com.nbn.adfeed.data.remote;

import com.google.gson.annotations.SerializedName;

public final class AiSearchRequest {
    @SerializedName("query")
    private String query;

    public AiSearchRequest(String query) {
        this.query = query;
    }

    private AiSearchRequest() {
    }

    public String getQuery() {
        return query;
    }
}
