package com.nbn.backend.store;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class JsonSeedStore {
    private final Map<String, String> rawJsonByFile;
    private final Map<String, JSONObject> documentsByFile;
    private final SeedStatus seedStatus;
    private final Path stateDirectory;

    private JsonSeedStore(
            Map<String, String> rawJsonByFile,
            Map<String, JSONObject> documentsByFile,
            Path stateDirectory
    ) {
        this.rawJsonByFile = new LinkedHashMap<>(rawJsonByFile);
        this.documentsByFile = new LinkedHashMap<>(documentsByFile);
        this.seedStatus = SeedStatus.fromLoadedFiles(rawJsonByFile.keySet());
        this.stateDirectory = stateDirectory;
    }

    public static JsonSeedStore loadDefault(ClassLoader classLoader) throws IOException {
        return loadDefault(classLoader, null);
    }

    public static JsonSeedStore loadDefault(ClassLoader classLoader, Path stateDirectory) throws IOException {
        Map<String, String> rawJsonByFile = new LinkedHashMap<>();
        Map<String, JSONObject> documentsByFile = new LinkedHashMap<>();
        for (String seedFile : SeedStatus.expectedSeedFiles()) {
            String rawJson = readEffectiveJson(classLoader, stateDirectory, seedFile);
            JSONObject document = parse(seedFile, rawJson);
            rawJsonByFile.put(seedFile, rawJson);
            documentsByFile.put(seedFile, document);
        }
        JsonSeedStore store = new JsonSeedStore(rawJsonByFile, documentsByFile, stateDirectory);
        store.validateRequiredStructure();
        return store;
    }

    public SeedStatus seedStatus() {
        return seedStatus;
    }

    public String rawJson(String seedFile) {
        return rawJsonByFile.get(seedFile);
    }

    public JSONObject documentCopy(String seedFile) {
        String rawJson = rawJson(seedFile);
        if (rawJson == null) {
            throw new IllegalArgumentException("Unknown seed file: " + seedFile);
        }
        return new JSONObject(rawJson);
    }

    public boolean persistenceEnabled() {
        return stateDirectory != null;
    }

    public String stateDirectoryPath() {
        return stateDirectory == null ? "" : stateDirectory.toAbsolutePath().normalize().toString();
    }

    public synchronized void writeState(String seedFile, JSONObject document) {
        if (stateDirectory == null) {
            rawJsonByFile.put(seedFile, document.toString());
            documentsByFile.put(seedFile, new JSONObject(document.toString()));
            return;
        }
        if (!rawJsonByFile.containsKey(seedFile)) {
            throw new IllegalArgumentException("Unknown seed file: " + seedFile);
        }
        try {
            Files.createDirectories(stateDirectory);
            String prettyJson = document.toString(2);
            Files.writeString(stateDirectory.resolve(seedFile), prettyJson, StandardCharsets.UTF_8);
            rawJsonByFile.put(seedFile, document.toString());
            documentsByFile.put(seedFile, new JSONObject(document.toString()));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write backend state file: " + seedFile, exception);
        }
    }

    public int feedChannelCount() {
        return document("home_feed.json").getJSONArray("channels").length();
    }

    public int feedItemCount() {
        return document("home_feed.json").getJSONObject("page").getJSONArray("items").length();
    }

    public int detailCount() {
        return document("ad_details.json").getJSONArray("details").length();
    }

    public int searchSuggestionCount() {
        return document("search_results.json").getJSONArray("suggestions").length();
    }

    public int searchResultCount() {
        return document("search_results.json").getJSONArray("results").length();
    }

    public int notificationCount() {
        return document("messages.json").getJSONArray("notifications").length();
    }

    public int conversationCount() {
        return document("messages.json").getJSONArray("conversations").length();
    }

    public int profilePostCount() {
        return document("profile.json").getJSONArray("posts").length();
    }

    public int reviewGroupCount() {
        return document("reviews.json").getJSONObject("reviewsByAd").length();
    }

    public int commentCount() {
        return document("reviews.json").getJSONArray("comments").length();
    }

    public int assetCount() {
        return document("app_config.json").getJSONArray("assetManifest").length();
    }

    public String domainSummaryJson() {
        return "{"
                + "\"feedChannels\":" + feedChannelCount() + ","
                + "\"feedItems\":" + feedItemCount() + ","
                + "\"adDetails\":" + detailCount() + ","
                + "\"searchSuggestions\":" + searchSuggestionCount() + ","
                + "\"searchResults\":" + searchResultCount() + ","
                + "\"notifications\":" + notificationCount() + ","
                + "\"conversations\":" + conversationCount() + ","
                + "\"profilePosts\":" + profilePostCount() + ","
                + "\"reviewGroups\":" + reviewGroupCount() + ","
                + "\"comments\":" + commentCount() + ","
                + "\"assets\":" + assetCount()
                + "}";
    }

    private JSONObject document(String seedFile) {
        JSONObject document = documentsByFile.get(seedFile);
        if (document == null) {
            throw new IllegalArgumentException("Unknown seed file: " + seedFile);
        }
        return document;
    }

    private void validateRequiredStructure() {
        requireArray("home_feed.json", "channels");
        document("home_feed.json").getJSONObject("page").getJSONArray("items");
        requireArray("ad_details.json", "details");
        requireArray("search_results.json", "suggestions");
        document("search_results.json").getJSONObject("session");
        requireArray("search_results.json", "messages");
        requireArray("search_results.json", "results");
        document("search_results.json").getJSONObject("fallback");
        document("messages.json").getJSONObject("notificationSummary");
        requireArray("messages.json", "notifications");
        requireArray("messages.json", "conversations");
        requireArray("messages.json", "messages");
        document("messages.json").getJSONObject("aiAssistantDigest");
        document("profile.json").getJSONObject("userProfile");
        requireArray("profile.json", "posts");
        requireArray("profile.json", "followers");
        requireArray("profile.json", "following");
        document("reviews.json").getJSONObject("reviewsByAd");
        requireArray("reviews.json", "comments");
        document("app_config.json").getJSONObject("remoteConfig");
        requireArray("app_config.json", "assetManifest");
        document("app_config.json").getJSONObject("analyticsSummary");
    }

    private void requireArray(String seedFile, String key) {
        document(seedFile).getJSONArray(key);
    }

    private static String readRequiredResource(ClassLoader classLoader, String resourcePath) throws IOException {
        try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing backend seed resource: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String readEffectiveJson(
            ClassLoader classLoader,
            Path stateDirectory,
            String seedFile
    ) throws IOException {
        String resourceJson = readRequiredResource(classLoader, "seed/" + seedFile);
        if (stateDirectory != null) {
            Path stateFile = stateDirectory.resolve(seedFile);
            if (Files.isRegularFile(stateFile)) {
                return mergeStateWithResourceSeed(seedFile, resourceJson, Files.readString(stateFile, StandardCharsets.UTF_8));
            }
        }
        return resourceJson;
    }

    private static JSONObject parse(String seedFile, String rawJson) {
        try {
            return new JSONObject(rawJson);
        } catch (JSONException exception) {
            throw new IllegalStateException("Invalid JSON seed file: " + seedFile, exception);
        }
    }

    private static String mergeStateWithResourceSeed(String seedFile, String resourceJson, String stateJson) {
        JSONObject seed = new JSONObject(resourceJson);
        JSONObject state = new JSONObject(stateJson);
        JSONObject merged = new JSONObject(state.toString());
        switch (seedFile) {
            case "home_feed.json" -> mergeHomeFeed(seed, state, merged);
            case "ad_details.json" -> merged.put("details", mergeArrayById(
                    seed.getJSONArray("details"),
                    state.getJSONArray("details"),
                    "adId"
            ));
            case "search_results.json" -> mergeSearchResults(seed, state, merged);
            case "messages.json" -> mergeMessages(seed, state, merged);
            case "profile.json" -> mergeProfile(seed, state, merged);
            case "reviews.json" -> mergeReviews(seed, state, merged);
            case "app_config.json" -> mergeAppConfig(seed, state, merged);
            default -> {
            }
        }
        return merged.toString();
    }

    private static void mergeHomeFeed(JSONObject seed, JSONObject state, JSONObject merged) {
        merged.put("channels", mergeArrayById(seed.getJSONArray("channels"), state.getJSONArray("channels"), "id"));
        JSONObject seedPage = seed.getJSONObject("page");
        JSONObject statePage = state.getJSONObject("page");
        JSONObject mergedPage = new JSONObject(statePage.toString());
        JSONArray items = mergeArrayById(seedPage.getJSONArray("items"), statePage.getJSONArray("items"), "adId");
        mergedPage.put("items", items);
        mergedPage.put("totalCount", items.length());
        merged.put("page", mergedPage);
    }

    private static void mergeSearchResults(JSONObject seed, JSONObject state, JSONObject merged) {
        merged.put("suggestions", mergeArrayById(
                seed.getJSONArray("suggestions"),
                state.getJSONArray("suggestions"),
                "suggestionId"
        ));
        merged.put("results", mergeArrayById(seed.getJSONArray("results"), state.getJSONArray("results"), "adId"));
        JSONObject seedFallback = seed.getJSONObject("fallback");
        JSONObject stateFallback = state.getJSONObject("fallback");
        JSONObject mergedFallback = new JSONObject(stateFallback.toString());
        mergedFallback.put("defaultAdIds", mergeStringArray(
                seedFallback.getJSONArray("defaultAdIds"),
                stateFallback.getJSONArray("defaultAdIds")
        ));
        merged.put("fallback", mergedFallback);
    }

    private static void mergeMessages(JSONObject seed, JSONObject state, JSONObject merged) {
        merged.put("notifications", mergeArrayById(
                seed.getJSONArray("notifications"),
                state.getJSONArray("notifications"),
                "notificationId"
        ));
        merged.put("conversations", mergeArrayById(
                seed.getJSONArray("conversations"),
                state.getJSONArray("conversations"),
                "conversationId"
        ));
        merged.put("messages", mergeArrayById(seed.getJSONArray("messages"), state.getJSONArray("messages"), "messageId"));
    }

    private static void mergeProfile(JSONObject seed, JSONObject state, JSONObject merged) {
        merged.put("currentUserId", state.optString("currentUserId", seed.optString("currentUserId", "")));
        JSONObject seedProfile = seed.getJSONObject("userProfile");
        JSONObject stateProfile = state.getJSONObject("userProfile");
        JSONObject mergedProfile = new JSONObject(stateProfile.toString());
        mergedProfile.put("achievements", mergeArrayById(
                seedProfile.getJSONArray("achievements"),
                stateProfile.getJSONArray("achievements"),
                "achievementId"
        ));
        merged.put("userProfile", mergedProfile);
        merged.put("posts", mergeArrayById(seed.getJSONArray("posts"), state.getJSONArray("posts"), "postId"));
        merged.put("followers", mergeArrayById(seed.getJSONArray("followers"), state.getJSONArray("followers"), "relationId"));
        merged.put("following", mergeArrayById(seed.getJSONArray("following"), state.getJSONArray("following"), "relationId"));
    }

    private static void mergeReviews(JSONObject seed, JSONObject state, JSONObject merged) {
        JSONObject seedReviews = seed.getJSONObject("reviewsByAd");
        JSONObject stateReviews = state.getJSONObject("reviewsByAd");
        JSONObject mergedReviews = new JSONObject(stateReviews.toString());
        for (String adId : seedReviews.keySet()) {
            JSONArray seedItems = seedReviews.getJSONArray(adId);
            JSONArray stateItems = stateReviews.optJSONArray(adId);
            mergedReviews.put(adId, stateItems == null ? new JSONArray(seedItems.toString()) : mergeArrayById(seedItems, stateItems, "reviewId"));
        }
        merged.put("reviewsByAd", mergedReviews);
        merged.put("comments", mergeArrayById(seed.getJSONArray("comments"), state.getJSONArray("comments"), "commentId"));
    }

    private static void mergeAppConfig(JSONObject seed, JSONObject state, JSONObject merged) {
        merged.put("assetManifest", mergeArrayById(
                seed.getJSONArray("assetManifest"),
                state.getJSONArray("assetManifest"),
                "assetId"
        ));
    }

    private static JSONArray mergeArrayById(JSONArray seedArray, JSONArray stateArray, String idKey) {
        Map<String, JSONObject> stateById = new LinkedHashMap<>();
        for (int index = 0; index < stateArray.length(); index++) {
            JSONObject item = stateArray.getJSONObject(index);
            stateById.put(item.optString(idKey), item);
        }
        Set<String> usedIds = new HashSet<>();
        JSONArray merged = new JSONArray();
        for (int index = 0; index < seedArray.length(); index++) {
            JSONObject seedItem = seedArray.getJSONObject(index);
            String id = seedItem.optString(idKey);
            JSONObject stateItem = stateById.get(id);
            merged.put(stateItem == null ? new JSONObject(seedItem.toString()) : mergeObject(seedItem, stateItem));
            usedIds.add(id);
        }
        for (int index = 0; index < stateArray.length(); index++) {
            JSONObject stateItem = stateArray.getJSONObject(index);
            String id = stateItem.optString(idKey);
            if (!usedIds.contains(id)) {
                merged.put(new JSONObject(stateItem.toString()));
            }
        }
        return merged;
    }

    private static JSONArray mergeStringArray(JSONArray seedArray, JSONArray stateArray) {
        Set<String> values = new HashSet<>();
        JSONArray merged = new JSONArray();
        appendUniqueStrings(merged, values, stateArray);
        appendUniqueStrings(merged, values, seedArray);
        return merged;
    }

    private static void appendUniqueStrings(JSONArray target, Set<String> values, JSONArray source) {
        for (int index = 0; index < source.length(); index++) {
            String value = source.optString(index, "");
            if (!value.isBlank() && values.add(value)) {
                target.put(value);
            }
        }
    }

    private static JSONObject mergeObject(JSONObject seedItem, JSONObject stateItem) {
        JSONObject merged = new JSONObject(seedItem.toString());
        for (String key : stateItem.keySet()) {
            merged.put(key, stateItem.get(key));
        }
        return merged;
    }
}
