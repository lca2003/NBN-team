package com.nbn.adfeed.data.mock;

import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.AdStats;
import com.nbn.adfeed.data.model.InteractionState;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class MockAdJsonParser {
    private MockAdJsonParser() {
    }

    public static List<AdItem> parse(String json) {
        try {
            JSONArray array = new JSONArray(json);
            List<AdItem> ads = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                ads.add(parseItem(array.getJSONObject(index)));
            }
            return ads;
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid mock ad JSON", exception);
        }
    }

    private static AdItem parseItem(JSONObject object) throws JSONException {
        JSONObject stats = object.optJSONObject("stats");
        JSONObject interaction = object.optJSONObject("interaction");
        AdContentType contentType = AdContentType.valueOf(object.optString("contentType", AdContentType.LARGE_IMAGE.name()));
        String channel = object.optString("channel");
        return new AdItem(
                object.getString("id"),
                object.optString("title"),
                object.optString("brand"),
                channel,
                object.optString("channelId", channel),
                object.optString("description"),
                object.optString("summary"),
                nullable(object.optString("imageUrl", null)),
                nullable(object.optString("thumbnailUrl", object.optString("imageUrl", null))),
                nullable(object.optString("videoUrl", null)),
                object.optString("offerText"),
                object.optString("ctaText"),
                object.optString("skuText"),
                object.optString("ratingText"),
                object.optString("deliveryText"),
                object.optString("stockText"),
                parseTags(object.optJSONArray("similarItems")),
                object.optString("distanceText"),
                object.optString("districtText"),
                object.optString("addressText"),
                object.optString("businessHoursText"),
                object.optString("navigationText"),
                object.optString("assetTheme", defaultAssetTheme(channel, contentType)),
                object.optString("visualLabel", defaultVisualLabel(object.optString("brand"), object.optString("title"))),
                object.optString("ctaIntent", defaultCtaIntent(channel, contentType)),
                contentType,
                parseTags(object.optJSONArray("tags")),
                new InteractionState(
                        interaction != null && interaction.optBoolean("liked", false),
                        interaction != null && interaction.optBoolean("collected", false)
                ),
                new AdStats(
                        stats == null ? 0 : stats.optInt("exposureCount", 0),
                        stats == null ? 0 : stats.optInt("clickCount", 0),
                        stats == null ? 0 : stats.optInt("likeCount", 0),
                        stats == null ? 0 : stats.optInt("collectCount", 0),
                        stats == null ? 0 : stats.optInt("shareCount", 0)
                ),
                object.optString("contentHash", null)
        );
    }

    private static List<String> parseTags(JSONArray array) throws JSONException {
        List<String> tags = new ArrayList<>();
        if (array == null) {
            return tags;
        }
        for (int index = 0; index < array.length(); index++) {
            tags.add(array.getString(index));
        }
        return tags;
    }

    private static String nullable(String value) {
        return value == null || value.trim().isEmpty() || "null".equals(value) ? null : value;
    }

    private static String defaultAssetTheme(String channel, AdContentType contentType) {
        if ("电商".equals(channel)) {
            return "commerce";
        }
        if ("本地".equals(channel)) {
            return "local";
        }
        if (contentType == AdContentType.VIDEO) {
            return "video";
        }
        return "featured";
    }

    private static String defaultVisualLabel(String brand, String title) {
        String safeBrand = brand == null ? "" : brand.trim();
        String safeTitle = title == null ? "" : title.trim();
        if (!safeBrand.isEmpty() && !safeTitle.isEmpty()) {
            return safeBrand + " · " + safeTitle;
        }
        return safeBrand.isEmpty() ? safeTitle : safeBrand;
    }

    private static String defaultCtaIntent(String channel, AdContentType contentType) {
        if ("电商".equals(channel)) {
            return "product";
        }
        if ("本地".equals(channel)) {
            return "store_navigation";
        }
        if (contentType == AdContentType.VIDEO) {
            return "video";
        }
        return "learn_more";
    }
}
