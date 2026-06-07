package com.nbn.backend.domain.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class CloudAiProvider {
    public static final String SOURCE = "remote_ai";
    public static final String ENV_API_KEY = "NBN_AI_API_KEY";
    public static final String ENV_ENDPOINT = "NBN_AI_ENDPOINT";
    public static final String ENV_MODEL = "NBN_AI_MODEL";

    private final String apiKey;
    private final String endpoint;
    private final String model;
    private final Transport transport;

    public CloudAiProvider(String apiKey, String endpoint, String model, Transport transport) {
        this.apiKey = normalize(apiKey);
        this.endpoint = normalize(endpoint);
        this.model = normalize(model);
        this.transport = transport;
    }

    public static CloudAiProvider fromEnvironment() {
        return new CloudAiProvider(
                System.getenv(ENV_API_KEY),
                System.getenv(ENV_ENDPOINT),
                System.getenv(ENV_MODEL),
                new JavaHttpTransport()
        );
    }

    public boolean configured() {
        return !apiKey.isBlank() && !endpoint.isBlank() && !model.isBlank();
    }

    public String model() {
        return model;
    }

    public String endpoint() {
        return endpoint;
    }

    public String completeText(String systemPrompt, String userPrompt) {
        if (!configured()) {
            return "";
        }
        JSONObject requestBody = new JSONObject()
                .put("model", model)
                .put("temperature", 0.2)
                .put("messages", new JSONArray()
                        .put(new JSONObject()
                                .put("role", "system")
                                .put("content", systemPrompt))
                        .put(new JSONObject()
                                .put("role", "user")
                                .put("content", userPrompt)));
        String responseBody = transport.postJson(endpoint, bearerToken(), requestBody.toString());
        return extractText(new JSONObject(responseBody));
    }

    private String bearerToken() {
        return "Bearer " + apiKey;
    }

    private static String extractText(JSONObject response) {
        String outputText = response.optString("output_text", "");
        if (!outputText.isBlank()) {
            return outputText.trim();
        }
        JSONArray choices = response.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message != null) {
                String content = message.optString("content", "");
                if (!content.isBlank()) {
                    return content.trim();
                }
            }
        }
        JSONArray output = response.optJSONArray("output");
        if (output != null) {
            for (int index = 0; index < output.length(); index++) {
                JSONArray content = output.getJSONObject(index).optJSONArray("content");
                if (content == null) {
                    continue;
                }
                for (int contentIndex = 0; contentIndex < content.length(); contentIndex++) {
                    JSONObject item = content.getJSONObject(contentIndex);
                    String text = item.optString("text", "");
                    if (!text.isBlank()) {
                        return text.trim();
                    }
                }
            }
        }
        return "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public interface Transport {
        String postJson(String endpoint, String authorizationHeader, String requestBody);
    }

    private static final class JavaHttpTransport implements Transport {
        private static final int TIMEOUT_SECONDS = 12;
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();

        @Override
        public String postJson(String endpoint, String authorizationHeader, String requestBody) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .header("Authorization", authorizationHeader)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("cloud AI HTTP " + response.statusCode());
                }
                return response.body();
            } catch (IOException exception) {
                throw new IllegalStateException("cloud AI request failed", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("cloud AI request interrupted", exception);
            }
        }
    }

    public JSONObject metadataJson() {
        return new JSONObject()
                .put("source", configured() ? SOURCE : "rule_fallback")
                .put("cloudConfigured", configured())
                .put("apiKeyEnv", ENV_API_KEY)
                .put("endpointEnv", ENV_ENDPOINT)
                .put("modelEnv", ENV_MODEL)
                .put("model", model);
    }
}
