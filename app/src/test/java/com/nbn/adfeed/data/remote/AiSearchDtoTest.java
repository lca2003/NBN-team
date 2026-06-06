package com.nbn.adfeed.data.remote;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class AiSearchDtoTest {
    private final Gson gson = new Gson();

    @Test
    public void requestSerializesQueryOnly() {
        AiSearchRequest request = new AiSearchRequest("student sports");

        JsonObject json = gson.toJsonTree(request).getAsJsonObject();

        assertEquals(1, json.entrySet().size());
        assertEquals("student sports", json.get("query").getAsString());
        assertFalse(json.has("channels"));
        assertFalse(json.has("limit"));
    }

    @Test
    public void responseDeserializesBackendContractFields() {
        AiSearchResponse response = gson.fromJson(
                "{"
                        + "\"requestId\":\"req-ai-search\","
                        + "\"code\":\"OK\","
                        + "\"message\":\"ok\","
                        + "\"data\":{"
                        + "\"messages\":["
                        + "{\"senderType\":\"user\",\"content\":\"student sports\"},"
                        + "{\"senderType\":\"ai\",\"content\":\"found ads\"}"
                        + "],"
                        + "\"results\":[{\"adId\":\"ad_001\"},{\"adId\":\"ad_002\"}],"
                        + "\"fallback\":{\"message\":\"fallback message\"},"
                        + "\"provider\":{\"source\":\"remote_ai\"}"
                        + "}"
                        + "}",
                AiSearchResponse.class
        );

        assertEquals("found ads", response.getAnswer());
        assertEquals(Arrays.asList("ad_001", "ad_002"), response.getMatchedAdIds());
        assertFalse(response.isFallback());
    }
}
