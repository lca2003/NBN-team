package com.nbn.backend.domain.ads;

import com.nbn.backend.domain.users.UserApiService;
import com.nbn.backend.domain.users.UserSession;
import com.nbn.backend.store.JsonSeedStore;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AdApiService {
    private final JsonSeedStore seedStore;
    private final UserSession session;
    private final UserApiService userApiService;
    private final JSONArray channels;
    private final JSONObject feedPage;
    private final Map<String, JSONObject> feedItemsByAdId = new LinkedHashMap<>();
    private final Map<String, JSONObject> detailsByAdId = new LinkedHashMap<>();

    public AdApiService(JsonSeedStore seedStore, UserSession session, UserApiService userApiService) {
        this.seedStore = seedStore;
        this.session = session;
        this.userApiService = userApiService;
        JSONObject homeFeed = seedStore.documentCopy("home_feed.json");
        JSONObject adDetails = seedStore.documentCopy("ad_details.json");
        this.channels = homeFeed.getJSONArray("channels");
        this.feedPage = homeFeed.getJSONObject("page");
        JSONArray feedItems = feedPage.getJSONArray("items");
        for (int index = 0; index < feedItems.length(); index++) {
            JSONObject item = feedItems.getJSONObject(index);
            normalizeAdItem(item);
            feedItemsByAdId.put(item.getString("adId"), item);
        }
        JSONArray details = adDetails.getJSONArray("details");
        for (int index = 0; index < details.length(); index++) {
            JSONObject detail = details.getJSONObject(index);
            detailsByAdId.put(detail.getString("adId"), detail);
        }
        persistHomeFeed();
    }

    public synchronized String channelsJson() {
        return "{\"channels\":" + new JSONArray(channels.toString()) + "}";
    }

    public synchronized String feedJson(URI requestUri) {
        Map<String, String> query = parseQuery(requestUri.getRawQuery());
        int limit = positiveInt(query.get("limit"), feedItemsByAdId.size());
        JSONArray items = new JSONArray();
        int count = 0;
        for (JSONObject item : feedItemsByAdId.values()) {
            if (count >= limit) {
                break;
            }
            refreshDerivedState(item);
            items.put(copy(item));
            count++;
        }
        boolean hasMore = count < feedItemsByAdId.size();
        String nextCursor = hasMore ? feedPage.optString("nextCursor", "") : "";
        JSONObject data = new JSONObject();
        data.put("channel", query.getOrDefault("channel", "featured"));
        data.put("cursor", query.getOrDefault("cursor", feedPage.optString("cursor", "")));
        data.put("nextCursor", nextCursor);
        data.put("hasMore", hasMore);
        data.put("totalCount", feedItemsByAdId.size());
        data.put("items", items);
        return data.toString();
    }

    public synchronized String adJson(String adId) {
        JSONObject item = feedItemsByAdId.get(adId);
        if (item == null) {
            JSONObject post = userApiService.postAsFeedItem(adId);
            return post == null ? null : "{\"ad\":" + post + "}";
        }
        refreshDerivedState(item);
        return "{\"ad\":" + copy(item) + "}";
    }

    public synchronized String detailJson(String adId) {
        JSONObject detail = detailsByAdId.get(adId);
        if (detail == null) {
            return null;
        }
        JSONObject detailCopy = copy(detail);
        JSONObject item = feedItemsByAdId.get(adId);
        if (item != null) {
            syncDetailStats(detailCopy, item);
        }
        syncDetailFeedback(detailCopy, adId);
        return "{\"detail\":" + detailCopy + "}";
    }

    public synchronized String relatedJson(String adId) {
        JSONObject detail = detailsByAdId.get(adId);
        if (detail == null) {
            return null;
        }
        JSONArray relatedItems = detail.optJSONArray("relatedItems");
        if (relatedItems == null) {
            relatedItems = new JSONArray();
        }
        return "{\"adId\":\"" + adId + "\",\"items\":" + new JSONArray(relatedItems.toString()) + "}";
    }

    public synchronized String applyInteraction(String adId, InteractionCommand command) {
        JSONObject item = feedItemsByAdId.get(adId);
        if (item == null) {
            return userApiService.applyPostInteraction(adId, command.name());
        }
        JSONObject stats = item.getJSONObject("stats");
        JSONObject interactionState = item.getJSONObject("interactionState");
        switch (command) {
            case LIKE -> setUserRelation(item, "likedUserIds", true);
            case UNLIKE -> setUserRelation(item, "likedUserIds", false);
            case COLLECT -> setUserRelation(item, "collectedUserIds", true);
            case UNCOLLECT -> setUserRelation(item, "collectedUserIds", false);
            case SHARE -> setUserRelation(item, "sharedUserIds", true);
            case CLICK -> increment(stats, "clickCount");
            case EXPOSURE -> increment(stats, "exposureCount");
        }
        refreshDerivedState(item);
        persistHomeFeed();
        JSONObject data = new JSONObject();
        data.put("adId", adId);
        data.put("currentUserId", currentUserId());
        data.put("stats", copy(stats));
        data.put("interactionState", copy(interactionState));
        data.put("likedUserIds", new JSONArray(item.getJSONArray("likedUserIds").toString()));
        data.put("collectedUserIds", new JSONArray(item.getJSONArray("collectedUserIds").toString()));
        return data.toString();
    }

    public synchronized void incrementCommentCount(String adId, int delta) {
        JSONObject item = feedItemsByAdId.get(adId);
        if (item == null) {
            userApiService.incrementPostCommentCount(adId, delta);
            return;
        }
        JSONObject stats = item.getJSONObject("stats");
        stats.put("commentCount", Math.max(0, stats.optInt("commentCount", 0) + delta));
        persistHomeFeed();
    }

    public synchronized void setCommentCount(String adId, int count) {
        JSONObject item = feedItemsByAdId.get(adId);
        if (item == null) {
            userApiService.setPostCommentCount(adId, count);
            return;
        }
        item.getJSONObject("stats").put("commentCount", Math.max(0, count));
        persistHomeFeed();
    }

    private void normalizeAdItem(JSONObject item) {
        ensureSystemAdminCreator(item);
        ensureUserArray(item, "likedUserIds", "liked");
        ensureUserArray(item, "collectedUserIds", "collected");
        ensureUserArray(item, "sharedUserIds", "shared");
        refreshDerivedState(item);
    }

    private void ensureSystemAdminCreator(JSONObject item) {
        JSONObject creator = item.optJSONObject("creator");
        if (creator == null) {
            creator = new JSONObject();
            item.put("creator", creator);
        }
        creator.put("userId", UserApiService.SYSTEM_ADMIN_USER_ID);
        creator.put("nickname", "NBN 系统管理员");
        creator.put("avatarUrl", "stitch_ui/images/stitch-09.png");
        creator.put("verified", true);
        creator.put("bio", "系统管理员账号，负责发布和维护广告内容。");
    }

    private void ensureUserArray(JSONObject item, String arrayKey, String legacyStateKey) {
        JSONArray userIds = item.optJSONArray(arrayKey);
        if (userIds == null) {
            userIds = new JSONArray();
            JSONObject interactionState = item.optJSONObject("interactionState");
            if (interactionState != null && interactionState.optBoolean(legacyStateKey, false)) {
                userIds.put(UserApiService.CURRENT_USER_ID);
            }
            item.put(arrayKey, userIds);
        }
        item.put(arrayKey, uniqueUserIds(userIds));
    }

    private void refreshDerivedState(JSONObject item) {
        JSONArray likedUserIds = uniqueUserIds(item.getJSONArray("likedUserIds"));
        JSONArray collectedUserIds = uniqueUserIds(item.getJSONArray("collectedUserIds"));
        JSONArray sharedUserIds = uniqueUserIds(item.getJSONArray("sharedUserIds"));
        item.put("likedUserIds", likedUserIds);
        item.put("collectedUserIds", collectedUserIds);
        item.put("sharedUserIds", sharedUserIds);

        JSONObject stats = item.getJSONObject("stats");
        stats.put("likeCount", likedUserIds.length());
        stats.put("collectCount", collectedUserIds.length());
        stats.put("shareCount", sharedUserIds.length());

        JSONObject interactionState = item.getJSONObject("interactionState");
        String userId = session.currentUserId();
        interactionState.put("liked", !userId.isBlank() && arrayContains(likedUserIds, userId));
        interactionState.put("collected", !userId.isBlank() && arrayContains(collectedUserIds, userId));
        interactionState.put("shared", !userId.isBlank() && arrayContains(sharedUserIds, userId));
        interactionState.put("followingCreator", currentUserFollows(item.getJSONObject("creator").optString("userId")));
    }

    private boolean currentUserFollows(String creatorUserId) {
        if (creatorUserId == null || creatorUserId.isBlank()) {
            return false;
        }
        JSONArray following = seedStore.documentCopy("profile.json").getJSONArray("following");
        for (int index = 0; index < following.length(); index++) {
            JSONObject relation = following.getJSONObject(index);
            if (session.currentUserId().equals(relation.optString("userId"))
                    && creatorUserId.equals(relation.optString("targetUserId"))
                    && relation.optBoolean("following", false)) {
                return true;
            }
        }
        return false;
    }

    private void setUserRelation(JSONObject item, String arrayKey, boolean active) {
        String userId = currentUserId();
        JSONArray userIds = item.getJSONArray(arrayKey);
        boolean contains = arrayContains(userIds, userId);
        if (active && !contains) {
            userIds.put(userId);
            return;
        }
        if (!active && contains) {
            removeValue(userIds, userId);
        }
    }

    private String currentUserId() {
        return session.requireCurrentUserId();
    }

    private static void increment(JSONObject stats, String countKey) {
        stats.put(countKey, stats.optInt(countKey, 0) + 1);
    }

    private static JSONArray uniqueUserIds(JSONArray source) {
        Set<String> seen = new HashSet<>();
        JSONArray result = new JSONArray();
        for (int index = 0; index < source.length(); index++) {
            String userId = source.optString(index, "").trim();
            if (!userId.isBlank() && seen.add(userId)) {
                result.put(userId);
            }
        }
        return result;
    }

    private static boolean arrayContains(JSONArray source, String value) {
        for (int index = 0; index < source.length(); index++) {
            if (value.equals(source.optString(index))) {
                return true;
            }
        }
        return false;
    }

    private static void removeValue(JSONArray source, String value) {
        for (int index = source.length() - 1; index >= 0; index--) {
            if (value.equals(source.optString(index))) {
                source.remove(index);
            }
        }
    }

    private static JSONObject copy(JSONObject source) {
        return new JSONObject(source.toString());
    }

    private void syncDetailStats(JSONObject detail, JSONObject item) {
        JSONObject detailStats = detail.optJSONObject("stats");
        JSONObject itemStats = item.optJSONObject("stats");
        if (detailStats == null || itemStats == null) {
            return;
        }
        detailStats.put("exposureText", formatCount(itemStats.optInt("exposureCount", 0)));
        detailStats.put("clickText", formatCount(itemStats.optInt("clickCount", 0)));
        detailStats.put("likeText", formatCount(itemStats.optInt("likeCount", 0)));
        detailStats.put("collectText", formatCount(itemStats.optInt("collectCount", 0)));
    }

    private void syncDetailFeedback(JSONObject detail, String adId) {
        JSONObject reviews = seedStore.documentCopy("reviews.json");
        JSONObject reviewsByAd = reviews.optJSONObject("reviewsByAd");
        JSONArray currentReviews = reviewsByAd == null ? null : reviewsByAd.optJSONArray(adId);
        detail.put("reviews", currentReviews == null ? new JSONArray() : new JSONArray(currentReviews.toString()));
        detail.put("comments", commentsForAd(reviews.optJSONArray("comments"), adId));
    }

    private JSONArray commentsForAd(JSONArray comments, String adId) {
        JSONArray result = new JSONArray();
        if (comments == null) {
            return result;
        }
        for (int index = 0; index < comments.length(); index++) {
            JSONObject comment = comments.getJSONObject(index);
            if ("ad".equals(comment.optString("targetType"))
                    && adId.equals(comment.optString("targetId"))) {
                result.put(copy(comment));
            }
        }
        return result;
    }

    private void persistHomeFeed() {
        seedStore.writeState("home_feed.json", new JSONObject()
                .put("channels", new JSONArray(channels.toString()))
                .put("page", copy(feedPage)));
    }

    private static String formatCount(int value) {
        return value < 1000 ? String.valueOf(Math.max(0, value)) : String.format("%,d", value);
    }

    private static int positiveInt(String rawValue, int defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(1, Integer.parseInt(rawValue));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return query;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int separator = pair.indexOf('=');
            String rawKey = separator >= 0 ? pair.substring(0, separator) : pair;
            String rawValue = separator >= 0 ? pair.substring(separator + 1) : "";
            query.put(decode(rawKey), decode(rawValue));
        }
        return query;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
