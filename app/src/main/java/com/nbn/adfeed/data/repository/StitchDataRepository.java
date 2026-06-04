package com.nbn.adfeed.data.repository;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class StitchDataRepository {
    private static final String DATA_ROOT = "stitch_data/";
    private static final String HOME_FEED = "home_feed.json";
    private static final String AD_DETAILS = "ad_details.json";
    private static final String SEARCH_RESULTS = "search_results.json";
    private static final String MESSAGES = "messages.json";
    private static final String PROFILE = "profile.json";
    private static final String REVIEWS = "reviews.json";
    private static final String APP_CONFIG = "app_config.json";

    private final Context context;

    public StitchDataRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public String homeFeedJson() {
        return readData(HOME_FEED);
    }

    public String adDetailsJson() {
        return readData(AD_DETAILS);
    }

    public String searchResultsJson() {
        return readData(SEARCH_RESULTS);
    }

    public String messagesJson() {
        return readData(MESSAGES);
    }

    public String profileJson() {
        return readData(PROFILE);
    }

    public String reviewsJson() {
        return readData(REVIEWS);
    }

    public String appConfigJson() {
        return readData(APP_CONFIG);
    }

    public String pagePayloadForUrl(String url) {
        String assetName = assetNameFromUrl(url);
        if ("search.html".equals(assetName)) {
            return object("search", searchResultsJson(), "appConfig", appConfigJson());
        }
        if ("messages.html".equals(assetName)) {
            return object("messages", messagesJson(), "appConfig", appConfigJson());
        }
        if ("profile.html".equals(assetName)) {
            return object("profile", profileJson(), "appConfig", appConfigJson());
        }
        if ("detail.html".equals(assetName)) {
            return object("details", adDetailsJson(), "reviews", reviewsJson(), "appConfig", appConfigJson());
        }
        return object("homeFeed", homeFeedJson(), "appConfig", appConfigJson());
    }

    private String readData(String fileName) {
        try {
            return readAsset(DATA_ROOT + fileName);
        } catch (IOException exception) {
            return "{}";
        }
    }

    private String readAsset(String path) throws IOException {
        try (InputStream inputStream = context.getAssets().open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString().trim();
        }
    }

    private static String assetNameFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }
        int slash = url.lastIndexOf('/');
        String assetName = slash >= 0 ? url.substring(slash + 1) : url;
        int query = assetName.indexOf('?');
        if (query >= 0) {
            assetName = assetName.substring(0, query);
        }
        int fragment = assetName.indexOf('#');
        return fragment >= 0 ? assetName.substring(0, fragment) : assetName;
    }

    private static String object(String key, String value) {
        return "{\"" + key + "\":" + jsonOrEmpty(value) + "}";
    }

    private static String object(String firstKey, String firstValue, String secondKey, String secondValue) {
        return "{\"" + firstKey + "\":" + jsonOrEmpty(firstValue)
                + ",\"" + secondKey + "\":" + jsonOrEmpty(secondValue) + "}";
    }

    private static String object(String firstKey, String firstValue, String secondKey, String secondValue,
                                 String thirdKey, String thirdValue) {
        return "{\"" + firstKey + "\":" + jsonOrEmpty(firstValue)
                + ",\"" + secondKey + "\":" + jsonOrEmpty(secondValue)
                + ",\"" + thirdKey + "\":" + jsonOrEmpty(thirdValue) + "}";
    }

    private static String jsonOrEmpty(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "{}" : normalized;
    }
}
