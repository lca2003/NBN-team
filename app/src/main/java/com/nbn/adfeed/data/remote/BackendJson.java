package com.nbn.adfeed.data.remote;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class BackendJson {
    private BackendJson() {
    }

    static JSONObject dataFromEnvelope(String responseBody, String domain) throws RemoteAdException {
        try {
            JSONObject envelope = new JSONObject(responseBody);
            if (!"OK".equals(envelope.optString("code"))) {
                throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, envelope.optString("message"));
            }
            JSONObject data = envelope.optJSONObject("data");
            if (data == null) {
                throw new RemoteAdException(
                        RemoteAdException.Reason.INVALID_RESPONSE,
                        "Backend " + domain + " response missing data"
                );
            }
            return data;
        } catch (JSONException exception) {
            throw invalidJson(exception);
        }
    }

    static JSONObject object(Object... keyValues) throws RemoteAdException {
        JSONObject json = new JSONObject();
        for (int index = 0; index < keyValues.length; index += 2) {
            put(json, String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return json;
    }

    static void put(JSONObject json, String key, Object value) throws RemoteAdException {
        try {
            json.put(key, value);
        } catch (JSONException exception) {
            throw invalidJson(exception);
        }
    }

    static JSONObject requiredObject(JSONObject data, String key, String domain) throws RemoteAdException {
        JSONObject value = data.optJSONObject(key);
        if (value == null) {
            throw new RemoteAdException(
                    RemoteAdException.Reason.INVALID_RESPONSE,
                    "Backend " + domain + " response missing " + key
            );
        }
        return value;
    }

    static List<String> strings(JSONArray values) {
        if (values == null) {
            return Collections.emptyList();
        }
        List<String> strings = new ArrayList<>();
        for (int index = 0; index < values.length(); index++) {
            String value = values.optString(index, "").trim();
            if (!value.isEmpty()) {
                strings.add(value);
            }
        }
        return strings;
    }

    static JSONArray stringArray(List<String> values) {
        JSONArray array = new JSONArray();
        if (values == null) {
            return array;
        }
        for (String value : values) {
            String safeValue = safe(value);
            if (!safeValue.isEmpty()) {
                array.put(safeValue);
            }
        }
        return array;
    }

    static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException exception) {
            return "";
        }
    }

    static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static RemoteAdException invalidJson(JSONException exception) {
        return new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, exception.getMessage());
    }
}
