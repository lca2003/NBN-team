package com.nbn.adfeed.data.remote;

import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.AdStats;
import com.nbn.adfeed.data.model.InteractionState;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class BackendAdJsonParser {
    private BackendAdJsonParser() {
    }

    static JSONObject dataFromEnvelope(String responseBody) throws RemoteAdException {
        try {
            JSONObject envelope = new JSONObject(responseBody);
            if (!"OK".equals(envelope.optString("code"))) {
                throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, envelope.optString("message"));
            }
            JSONObject data = envelope.optJSONObject("data");
            if (data == null) {
                throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, "Backend response missing data");
            }
            return data;
        } catch (JSONException exception) {
            throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, exception.getMessage());
        }
    }

    static PageResult<AdItem> parseFeed(JSONObject data, PageRequest request, String source) throws RemoteAdException {
        try {
            JSONArray itemsJson = data.getJSONArray("items");
            List<AdItem> items = new ArrayList<>();
            String channel = data.optString("channel", request.getChannel());
            for (int index = 0; index < itemsJson.length(); index++) {
                items.add(parseAd(itemsJson.getJSONObject(index), channel));
            }
            return new PageResult<>(
                    items,
                    data.optString("cursor", request.getCursor()),
                    data.optString("nextCursor", null),
                    data.optBoolean("hasMore", false),
                    request.getPageNumber(),
                    request.getPageSize(),
                    data.optInt("totalCount", items.size()),
                    source
            );
        } catch (JSONException exception) {
            throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, exception.getMessage());
        }
    }

    static AdItem parseAd(JSONObject object, String pageChannel) throws RemoteAdException {
        try {
            JSONObject cover = object.optJSONObject("cover");
            JSONObject video = object.optJSONObject("video");
            JSONObject stats = object.optJSONObject("stats");
            JSONObject interactionState = object.optJSONObject("interactionState");
            String category = object.optString("category", pageChannel);
            String imageUrl = cover == null ? null : nullable(cover.optString("url", null));
            String videoUrl = video == null ? null : nullable(video.optString("url", null));
            return new AdItem(
                    object.getString("adId"),
                    object.optString("title"),
                    object.optString("brand"),
                    category,
                    normalizeChannelId(pageChannel, category),
                    object.optString("description"),
                    object.optString("subtitle"),
                    imageUrl,
                    imageUrl,
                    videoUrl,
                    "",
                    "查看详情",
                    parseContentType(object.optString("adType")),
                    parseTags(object.optJSONArray("tags")),
                    new InteractionState(
                            interactionState != null && interactionState.optBoolean("liked", false),
                            interactionState != null && interactionState.optBoolean("collected", false)
                    ),
                    new AdStats(
                            stats == null ? 0 : stats.optInt("exposureCount", 0),
                            stats == null ? 0 : stats.optInt("clickCount", 0),
                            stats == null ? 0 : stats.optInt("likeCount", 0),
                            stats == null ? 0 : stats.optInt("collectCount", 0),
                            stats == null ? 0 : stats.optInt("shareCount", 0)
                    ),
                    object.optString("adId")
            );
        } catch (JSONException exception) {
            throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, exception.getMessage());
        }
    }

    private static AdContentType parseContentType(String rawType) {
        try {
            return AdContentType.valueOf(rawType);
        } catch (IllegalArgumentException exception) {
            return AdContentType.LARGE_IMAGE;
        }
    }

    private static List<String> parseTags(JSONArray tagsJson) throws JSONException {
        List<String> tags = new ArrayList<>();
        if (tagsJson == null) {
            return tags;
        }
        for (int index = 0; index < tagsJson.length(); index++) {
            Object tag = tagsJson.get(index);
            if (tag instanceof JSONObject) {
                tags.add(((JSONObject) tag).optString("name"));
            } else {
                tags.add(String.valueOf(tag));
            }
        }
        return tags;
    }

    private static String normalizeChannelId(String pageChannel, String category) {
        String normalized = pageChannel == null ? "" : pageChannel.trim();
        return normalized.isEmpty() ? category : normalized;
    }

    private static String nullable(String value) {
        return value == null || value.trim().isEmpty() || "null".equals(value) ? null : value;
    }
}
