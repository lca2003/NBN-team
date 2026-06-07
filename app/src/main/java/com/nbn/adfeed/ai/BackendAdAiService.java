package com.nbn.adfeed.ai;

import com.nbn.adfeed.data.remote.BackendConfig;
import com.nbn.adfeed.data.remote.HttpApiClient;
import com.nbn.adfeed.data.remote.RemoteAdException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BackendAdAiService implements AdAiService {
    public interface Transport {
        String post(String path) throws RemoteAdException;
    }

    private final Transport transport;
    private final AdAiService fallback;

    public BackendAdAiService(AdAiService fallback) {
        this(defaultTransport(), fallback);
    }

    public BackendAdAiService(Transport transport, AdAiService fallback) {
        this.transport = transport == null ? defaultTransport() : transport;
        this.fallback = fallback;
    }

    @Override
    public AiResponse<String> getAiSummary(String adId) {
        try {
            JSONObject data = dataFromEnvelope(transport.post("/v1/ai/ads/" + encode(adId) + "/summary"));
            String summary = data.optString("summary", "");
            if (summary.isBlank()) {
                return fallbackSummary(adId, "Backend AI summary is empty", null);
            }
            return AiResponse.success(summary, source(data.optString("source")), false);
        } catch (RemoteAdException exception) {
            return fallbackSummary(adId, "Backend AI summary unavailable", exception);
        }
    }

    @Override
    public AiResponse<List<String>> getAiTags(String adId) {
        try {
            JSONObject data = dataFromEnvelope(transport.post("/v1/ai/ads/" + encode(adId) + "/tags"));
            List<String> tags = tags(data.optJSONArray("tags"));
            if (tags.isEmpty()) {
                return fallbackTags(adId, "Backend AI tags are empty", null);
            }
            return AiResponse.success(tags, source(data.optString("source")), false);
        } catch (RemoteAdException | JSONException exception) {
            return fallbackTags(adId, "Backend AI tags unavailable", exception);
        }
    }

    private AiResponse<String> fallbackSummary(String adId, String message, Throwable error) {
        if (fallback == null) {
            return AiResponse.failure("", AiOutputSource.RULE_FALLBACK, message, error);
        }
        return fallback.getAiSummary(adId);
    }

    private AiResponse<List<String>> fallbackTags(String adId, String message, Throwable error) {
        if (fallback == null) {
            return AiResponse.failure(Collections.emptyList(), AiOutputSource.RULE_FALLBACK, message, error);
        }
        return fallback.getAiTags(adId);
    }

    private static Transport defaultTransport() {
        return path -> {
            RemoteAdException lastException = null;
            for (BackendConfig candidate : BackendConfig.defaultCandidates()) {
                try {
                    return new HttpApiClient(candidate).post(path);
                } catch (RemoteAdException exception) {
                    lastException = exception;
                }
            }
            throw lastException == null
                    ? new RemoteAdException(RemoteAdException.Reason.NETWORK, "Backend AI unavailable")
                    : lastException;
        };
    }

    private static JSONObject dataFromEnvelope(String responseBody) throws RemoteAdException {
        try {
            JSONObject envelope = new JSONObject(responseBody);
            if (!"OK".equals(envelope.optString("code"))) {
                throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, envelope.optString("message"));
            }
            JSONObject data = envelope.optJSONObject("data");
            if (data == null) {
                throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, "Backend AI response missing data");
            }
            return data;
        } catch (JSONException exception) {
            throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, exception.getMessage());
        }
    }

    private static List<String> tags(JSONArray tagsJson) throws JSONException {
        if (tagsJson == null) {
            return Collections.emptyList();
        }
        List<String> tags = new ArrayList<>();
        for (int index = 0; index < tagsJson.length(); index++) {
            String tag = tagsJson.optString(index, "").trim();
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private static AiOutputSource source(String rawSource) {
        for (AiOutputSource source : AiOutputSource.values()) {
            if (source.getWireName().equals(rawSource)) {
                return source;
            }
        }
        return AiOutputSource.RULE_FALLBACK;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException exception) {
            return "";
        }
    }
}
