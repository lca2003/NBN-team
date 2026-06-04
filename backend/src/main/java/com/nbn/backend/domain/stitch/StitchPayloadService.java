package com.nbn.backend.domain.stitch;

import com.nbn.backend.domain.users.UserApiService;
import com.nbn.backend.domain.users.UserSession;
import com.nbn.backend.store.JsonSeedStore;

import org.json.JSONArray;
import org.json.JSONObject;

public final class StitchPayloadService {
    private final JsonSeedStore seedStore;
    private final UserSession session;
    private final UserApiService userApiService;

    public StitchPayloadService(JsonSeedStore seedStore, UserSession session, UserApiService userApiService) {
        this.seedStore = seedStore;
        this.session = session;
        this.userApiService = userApiService;
    }

    public String pagePayloadJson(String pageName) {
        return switch (normalize(pageName)) {
            case "home" -> homePayloadJson();
            case "search" -> object("search", "search_results.json", "appConfig", "app_config.json");
            case "messages" -> messagesPayloadJson();
            case "profile" -> profilePayloadJson();
            case "detail" -> detailPayloadJson();
            default -> null;
        };
    }

    private String homePayloadJson() {
        JSONObject homeFeed = seedStore.documentCopy("home_feed.json");
        JSONObject page = homeFeed.getJSONObject("page");
        JSONArray mergedItems = new JSONArray();
        JSONArray notePosts = userApiService.allNotePosts();
        for (int index = notePosts.length() - 1; index >= 0; index--) {
            JSONObject post = notePosts.getJSONObject(index);
            JSONObject feedItem = userApiService.postAsFeedItem(post.optString("postId"));
            if (feedItem != null) {
                feedItem.getJSONObject("stats").put("commentCount", commentCount("post", feedItem.optString("adId")));
                mergedItems.put(feedItem);
            }
        }
        JSONArray adItems = page.getJSONArray("items");
        for (int index = 0; index < adItems.length(); index++) {
            JSONObject item = copy(adItems.getJSONObject(index));
            refreshAdState(item);
            mergedItems.put(item);
        }
        page.put("items", mergedItems);
        page.put("totalCount", mergedItems.length());
        homeFeed.put("page", page);
        return new JSONObject()
                .put("homeFeed", homeFeed)
                .put("appConfig", seedStore.documentCopy("app_config.json"))
                .toString();
    }

    private String profilePayloadJson() {
        JSONObject profile = seedStore.documentCopy("profile.json");
        if (!session.authenticated()) {
            return new JSONObject()
                    .put("profile", new JSONObject()
                            .put("authenticated", false)
                            .put("userProfile", JSONObject.NULL)
                            .put("posts", new JSONArray())
                            .put("followers", new JSONArray())
                            .put("following", new JSONArray()))
                    .put("appConfig", seedStore.documentCopy("app_config.json"))
                    .toString();
        }
        String userId = session.currentUserId();
        JSONObject userProfile = findUserProfile(profile, userId);
        JSONArray posts = postsForTab(userId, "notes");
        JSONArray collections = postsForTab(userId, "collections");
        JSONArray liked = postsForTab(userId, "liked");
        JSONArray followers = filterActiveRelations(profile.optJSONArray("followers"), "targetUserId", userId);
        JSONArray following = filterActiveRelations(profile.optJSONArray("following"), "userId", userId);

        userProfile.put("stats", new JSONObject(userApiService.statsJson()).getJSONObject("stats"));
        profile.put("userProfile", userProfile);
        profile.put("posts", posts);
        profile.put("collections", collections);
        profile.put("liked", liked);
        profile.put("followers", followers);
        profile.put("following", following);

        return new JSONObject()
                .put("profile", profile)
                .put("appConfig", seedStore.documentCopy("app_config.json"))
                .toString();
    }

    private String detailPayloadJson() {
        JSONObject adDetails = seedStore.documentCopy("ad_details.json");
        JSONObject homeFeed = seedStore.documentCopy("home_feed.json");
        JSONObject reviews = seedStore.documentCopy("reviews.json");
        JSONArray details = adDetails.getJSONArray("details");
        for (int index = 0; index < details.length(); index++) {
            JSONObject detail = details.getJSONObject(index);
            String adId = detail.optString("adId");
            syncDetailStats(detail, homeFeed, adId);
            detail.put("reviews", reviewsForAd(reviews, adId));
            detail.put("comments", commentsForAd(reviews, adId));
        }
        return new JSONObject()
                .put("details", adDetails)
                .put("reviews", reviews)
                .put("appConfig", seedStore.documentCopy("app_config.json"))
                .toString();
    }

    private String messagesPayloadJson() {
        JSONObject messages = seedStore.documentCopy("messages.json");
        messages.put("notificationSummary", computedNotificationSummary(messages.optJSONArray("notifications")));
        return new JSONObject()
                .put("messages", messages)
                .put("appConfig", seedStore.documentCopy("app_config.json"))
                .toString();
    }

    private JSONObject computedNotificationSummary(JSONArray notifications) {
        int like = 0;
        int collect = 0;
        int follower = 0;
        int comment = 0;
        if (notifications != null) {
            for (int index = 0; index < notifications.length(); index++) {
                JSONObject notification = notifications.getJSONObject(index);
                if (notification.optBoolean("read", false)) {
                    continue;
                }
                switch (notification.optString("type")) {
                    case "like" -> like++;
                    case "collect" -> collect++;
                    case "follower" -> follower++;
                    case "comment" -> comment++;
                    default -> {
                    }
                }
            }
        }
        return new JSONObject()
                .put("likeUnreadCount", like)
                .put("collectUnreadCount", collect)
                .put("followerUnreadCount", follower)
                .put("commentUnreadCount", comment)
                .put("totalUnreadCount", like + collect + follower + comment);
    }

    private void syncDetailStats(JSONObject detail, JSONObject homeFeed, String adId) {
        JSONObject item = feedItem(homeFeed, adId);
        JSONObject detailStats = detail.optJSONObject("stats");
        JSONObject itemStats = item == null ? null : item.optJSONObject("stats");
        if (detailStats == null || itemStats == null) {
            return;
        }
        detailStats.put("exposureText", formatCount(itemStats.optInt("exposureCount", 0)));
        detailStats.put("clickText", formatCount(itemStats.optInt("clickCount", 0)));
        detailStats.put("likeText", formatCount(itemStats.optInt("likeCount", 0)));
        detailStats.put("collectText", formatCount(itemStats.optInt("collectCount", 0)));
    }

    private JSONObject feedItem(JSONObject homeFeed, String adId) {
        JSONObject page = homeFeed.optJSONObject("page");
        JSONArray items = page == null ? null : page.optJSONArray("items");
        if (items == null) {
            return null;
        }
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.getJSONObject(index);
            if (adId.equals(item.optString("adId"))) {
                return item;
            }
        }
        return null;
    }

    private void refreshAdState(JSONObject item) {
        JSONArray likedUserIds = item.optJSONArray("likedUserIds");
        JSONArray collectedUserIds = item.optJSONArray("collectedUserIds");
        JSONArray sharedUserIds = item.optJSONArray("sharedUserIds");
        if (likedUserIds == null) {
            likedUserIds = new JSONArray();
            item.put("likedUserIds", likedUserIds);
        }
        if (collectedUserIds == null) {
            collectedUserIds = new JSONArray();
            item.put("collectedUserIds", collectedUserIds);
        }
        if (sharedUserIds == null) {
            sharedUserIds = new JSONArray();
            item.put("sharedUserIds", sharedUserIds);
        }
        JSONObject stats = item.optJSONObject("stats");
        if (stats == null) {
            stats = new JSONObject();
            item.put("stats", stats);
        }
        stats.put("likeCount", likedUserIds.length());
        stats.put("collectCount", collectedUserIds.length());
        stats.put("shareCount", sharedUserIds.length());
        JSONObject state = item.optJSONObject("interactionState");
        if (state == null) {
            state = new JSONObject();
            item.put("interactionState", state);
        }
        String userId = session.currentUserId();
        state.put("liked", !userId.isBlank() && arrayContains(likedUserIds, userId));
        state.put("collected", !userId.isBlank() && arrayContains(collectedUserIds, userId));
        state.put("shared", !userId.isBlank() && arrayContains(sharedUserIds, userId));
        JSONObject creator = item.optJSONObject("creator");
        state.put("followingCreator", creator != null && !userId.isBlank()
                && follows(userId, creator.optString("userId")));
    }

    private JSONObject findUserProfile(JSONObject profile, String userId) {
        JSONObject current = profile.getJSONObject("userProfile");
        if (userId.equals(current.optString("userId"))) {
            return current;
        }
        JSONArray users = profile.optJSONArray("users");
        if (users != null) {
            for (int index = 0; index < users.length(); index++) {
                JSONObject user = users.getJSONObject(index);
                if (userId.equals(user.optString("userId"))) {
                    return copy(user);
                }
            }
        }
        throw new IllegalArgumentException("user not found");
    }

    private boolean follows(String userId, String targetUserId) {
        JSONArray following = seedStore.documentCopy("profile.json").optJSONArray("following");
        if (following == null) {
            return false;
        }
        for (int index = 0; index < following.length(); index++) {
            JSONObject relation = following.getJSONObject(index);
            if (userId.equals(relation.optString("userId"))
                    && targetUserId.equals(relation.optString("targetUserId"))
                    && relation.optBoolean("following", false)) {
                return true;
            }
        }
        return false;
    }

    private int commentCount(String targetType, String targetId) {
        JSONArray comments = seedStore.documentCopy("reviews.json").optJSONArray("comments");
        if (comments == null) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < comments.length(); index++) {
            JSONObject comment = comments.getJSONObject(index);
            if (targetType.equals(comment.optString("targetType"))
                    && targetId.equals(comment.optString("targetId"))) {
                count++;
            }
        }
        return count;
    }

    private JSONArray reviewsForAd(JSONObject reviews, String adId) {
        JSONObject reviewsByAd = reviews.optJSONObject("reviewsByAd");
        if (reviewsByAd == null) {
            return new JSONArray();
        }
        JSONArray items = reviewsByAd.optJSONArray(adId);
        return items == null ? new JSONArray() : new JSONArray(items.toString());
    }

    private JSONArray commentsForAd(JSONObject reviews, String adId) {
        JSONArray comments = reviews.optJSONArray("comments");
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

    private JSONArray filterActiveRelations(JSONArray source, String key, String userId) {
        JSONArray result = new JSONArray();
        if (source == null) {
            return result;
        }
        for (int index = 0; index < source.length(); index++) {
            JSONObject relation = source.getJSONObject(index);
            if (userId.equals(relation.optString(key)) && relation.optBoolean("following", false)) {
                result.put(copy(relation));
            }
        }
        return result;
    }

    private JSONArray postsForTab(String userId, String tab) {
        return new JSONObject(userApiService.postsJson(userId, tab)).getJSONArray("items");
    }

    private boolean arrayContains(JSONArray source, String value) {
        if (source == null) {
            return false;
        }
        for (int index = 0; index < source.length(); index++) {
            if (value.equals(source.optString(index))) {
                return true;
            }
        }
        return false;
    }

    private JSONObject copy(JSONObject source) {
        return new JSONObject(source.toString());
    }

    private static String formatCount(int value) {
        return value < 1000 ? String.valueOf(Math.max(0, value)) : String.format("%,d", value);
    }

    private String object(String firstKey, String firstFile, String secondKey, String secondFile) {
        return "{"
                + "\"" + firstKey + "\":" + seedStore.rawJson(firstFile) + ","
                + "\"" + secondKey + "\":" + seedStore.rawJson(secondFile)
                + "}";
    }

    private String object(
            String firstKey,
            String firstFile,
            String secondKey,
            String secondFile,
            String thirdKey,
            String thirdFile
    ) {
        return "{"
                + "\"" + firstKey + "\":" + seedStore.rawJson(firstFile) + ","
                + "\"" + secondKey + "\":" + seedStore.rawJson(secondFile) + ","
                + "\"" + thirdKey + "\":" + seedStore.rawJson(thirdFile)
                + "}";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
