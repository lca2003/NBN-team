package com.nbn.backend.domain.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.nbn.backend.BackendServer;
import com.nbn.backend.store.JsonSeedStore;

import org.json.JSONObject;
import org.junit.Test;

public final class CloudAiProviderTest {
    @Test
    public void providerRequiresKeyEndpointAndModel() {
        CloudAiProvider provider = new CloudAiProvider("key", "", "model", (endpoint, auth, body) -> "{}");

        assertFalse(provider.configured());
        assertEquals("rule_fallback", provider.metadataJson().getString("source"));
    }

    @Test
    public void providerPostsOpenAiCompatibleMessagesAndParsesChoiceText() {
        CapturingTransport transport = new CapturingTransport(
                "{\"choices\":[{\"message\":{\"content\":\"云端摘要\"}}]}"
        );
        CloudAiProvider provider = new CloudAiProvider(
                "secret",
                "https://ai.example.test/v1/chat/completions",
                "demo-model",
                transport
        );

        String text = provider.completeText("system", "user");

        assertEquals("云端摘要", text);
        assertEquals("https://ai.example.test/v1/chat/completions", transport.endpoint);
        assertEquals("Bearer secret", transport.authorizationHeader);
        JSONObject requestBody = new JSONObject(transport.requestBody);
        assertEquals("demo-model", requestBody.getString("model"));
        assertEquals("system", requestBody.getJSONArray("messages").getJSONObject(0).getString("content"));
        assertEquals("user", requestBody.getJSONArray("messages").getJSONObject(1).getString("content"));
    }

    @Test
    public void aiServiceUsesCloudProviderForSummaryTagsAndSearchWhenConfigured() throws Exception {
        CloudAiProvider provider = new CloudAiProvider(
                "secret",
                "https://ai.example.test/v1/chat/completions",
                "demo-model",
                (endpoint, auth, body) -> {
                    if (body.contains("JSON")) {
                        return "{\"choices\":[{\"message\":{\"content\":\"[\\\"云端标签\\\",\\\"学生党\\\",\\\"通勤\\\"]\"}}]}";
                    }
                    return "{\"choices\":[{\"message\":{\"content\":\"云端生成结果\"}}]}";
                }
        );
        AiApiService service = new AiApiService(
                JsonSeedStore.loadDefault(BackendServer.class.getClassLoader()),
                provider
        );

        JSONObject summary = new JSONObject(service.summaryJson("ad_001"));
        JSONObject tags = new JSONObject(service.tagsJson("ad_001"));
        JSONObject search = new JSONObject(service.searchJson("{\"sessionId\":\"cloud_search_001\",\"query\":\"通勤\"}"));

        assertEquals("remote_ai", summary.getString("source"));
        assertEquals("云端生成结果", summary.getString("summary"));
        assertEquals("remote_ai", tags.getString("source"));
        assertEquals("云端标签", tags.getJSONArray("tags").getString(0));
        assertEquals("remote_ai", search.getJSONObject("provider").getString("source"));
        assertTrue(search.getJSONArray("messages").getJSONObject(1).getString("content").contains("云端生成结果"));
    }

    private static final class CapturingTransport implements CloudAiProvider.Transport {
        private final String responseBody;
        private String endpoint;
        private String authorizationHeader;
        private String requestBody;

        private CapturingTransport(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public String postJson(String endpoint, String authorizationHeader, String requestBody) {
            this.endpoint = endpoint;
            this.authorizationHeader = authorizationHeader;
            this.requestBody = requestBody;
            return responseBody;
        }
    }
}
