package com.nbn.backend.domain.reviews;

import com.nbn.backend.domain.ads.AdApiService;
import com.nbn.backend.domain.users.UserApiService;
import com.nbn.backend.domain.users.UserSession;
import com.nbn.backend.store.JsonSeedStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ReviewCommentApiService {
    private final JsonSeedStore seedStore;
    private final AdApiService adApiService;
    private final UserSession session;
    private final JSONObject reviewsByAd;
    private final JSONArray comments;

    public ReviewCommentApiService(JsonSeedStore seedStore, AdApiService adApiService, UserSession session) {
        this.seedStore = seedStore;
        this.adApiService = adApiService;
        this.session = session;
        JSONObject seed = seedStore.documentCopy("reviews.json");
        this.reviewsByAd = seed.getJSONObject("reviewsByAd");
        this.comments = seed.getJSONArray("comments");
        normalizeReviews();
        syncAllCommentCounts();
        persistReviews();
    }

    public synchronized String reviewsJson(String adId, URI requestUri) {
        JSONArray reviews = reviewsForAd(adId);
        Query query = Query.from(requestUri);
        JSONArray items = new JSONArray();
        int cursor = query.cursor();
        int limit = query.limit(reviews.length());
        int end = Math.min(reviews.length(), cursor + limit);
        for (int index = cursor; index < end; index++) {
            JSONObject review = reviews.getJSONObject(index);
            refreshReviewState(review);
            items.put(copy(review));
        }
        return new JSONObject()
                .put("adId", adId)
                .put("cursor", cursor == 0 ? "" : String.valueOf(cursor))
                .put("nextCursor", end < reviews.length() ? String.valueOf(end) : "")
                .put("hasMore", end < reviews.length())
                .put("totalCount", reviews.length())
                .put("items", items)
                .toString();
    }

    public synchronized String createReview(String adId, String requestBody) {
        JSONObject body = objectOrEmpty(requestBody);
        JSONObject author = author(body.optString("userId", session.currentUserId()));
        JSONArray reviews = reviewsForAd(adId);
        JSONObject review = new JSONObject();
        review.put("reviewId", body.optString("reviewId", "review_" + UUID.randomUUID()));
        review.put("userId", author.getString("userId"));
        review.put("userAvatarUrl", author.optString("avatarUrl"));
        review.put("nickname", author.optString("nickname"));
        review.put("timeText", body.optString("timeText", "刚刚"));
        review.put("content", requiredContent(body));
        review.put("likedUserIds", new JSONArray());
        refreshReviewState(review);
        reviews.put(review);
        reviewsByAd.put(adId, reviews);
        persistReviews();
        return "{\"review\":" + copy(review) + "}";
    }

    public synchronized String likeReview(String reviewId) {
        JSONObject review = findReview(reviewId);
        if (review == null) {
            return null;
        }
        JSONArray likedUserIds = review.getJSONArray("likedUserIds");
        String userId = session.requireCurrentUserId();
        if (!arrayContains(likedUserIds, userId)) {
            likedUserIds.put(userId);
        }
        refreshReviewState(review);
        persistReviews();
        return "{\"review\":" + copy(review) + "}";
    }

    public synchronized String unlikeReview(String reviewId) {
        JSONObject review = findReview(reviewId);
        if (review == null) {
            return null;
        }
        removeValue(review.getJSONArray("likedUserIds"), session.requireCurrentUserId());
        refreshReviewState(review);
        persistReviews();
        return "{\"review\":" + copy(review) + "}";
    }

    public synchronized String commentsJson(URI requestUri) {
        Query query = Query.from(requestUri);
        String targetType = query.value("targetType");
        String targetId = query.value("targetId");
        JSONArray items = new JSONArray();
        for (int index = 0; index < comments.length(); index++) {
            JSONObject comment = comments.getJSONObject(index);
            boolean matchTargetType = targetType.isBlank() || targetType.equals(comment.optString("targetType"));
            boolean matchTargetId = targetId.isBlank() || targetId.equals(comment.optString("targetId"));
            if (matchTargetType && matchTargetId) {
                items.put(copy(comment));
            }
        }
        return new JSONObject()
                .put("targetType", targetType)
                .put("targetId", targetId)
                .put("totalCount", items.length())
                .put("items", items)
                .toString();
    }

    public synchronized String createComment(String requestBody) {
        JSONObject body = objectOrEmpty(requestBody);
        JSONObject author = author(body.optString("userId", session.currentUserId()));
        String targetType = body.optString("targetType", "ad");
        String targetId = body.optString("targetId", "");
        JSONObject comment = new JSONObject();
        comment.put("commentId", body.optString("commentId", "comment_" + UUID.randomUUID()));
        comment.put("targetType", targetType);
        comment.put("targetId", targetId);
        comment.put("parentCommentId", body.optString("parentCommentId", ""));
        comment.put("userId", author.getString("userId"));
        comment.put("nickname", author.optString("nickname"));
        comment.put("userAvatarUrl", author.optString("avatarUrl"));
        comment.put("content", requiredContent(body));
        comment.put("timeText", body.optString("timeText", "刚刚"));
        comments.put(comment);
        syncCommentCount(targetType, targetId);
        persistReviews();
        return "{\"comment\":" + copy(comment) + "}";
    }

    public synchronized String deleteComment(String commentId) {
        for (int index = 0; index < comments.length(); index++) {
            JSONObject comment = comments.getJSONObject(index);
            if (commentId.equals(comment.optString("commentId"))
                    && session.requireCurrentUserId().equals(comment.optString("userId"))) {
                JSONObject removed = (JSONObject) comments.remove(index);
                syncCommentCount(removed.optString("targetType"), removed.optString("targetId"));
                persistReviews();
                return new JSONObject()
                        .put("deleted", true)
                        .put("comment", copy(removed))
                        .toString();
            }
        }
        throw new IllegalArgumentException("comment not found");
    }

    private void syncAllCommentCounts() {
        for (int index = 0; index < comments.length(); index++) {
            JSONObject comment = comments.getJSONObject(index);
            syncCommentCount(comment.optString("targetType"), comment.optString("targetId"));
        }
    }

    private void syncCommentCount(String targetType, String targetId) {
        if (!"ad".equals(targetType) && !"post".equals(targetType)) {
            return;
        }
        adApiService.setCommentCount(targetId, countComments(targetType, targetId));
    }

    private int countComments(String targetType, String targetId) {
        int count = 0;
        for (int index = 0; index < comments.length(); index++) {
            JSONObject comment = comments.getJSONObject(index);
            if (targetType.equals(comment.optString("targetType")) && targetId.equals(comment.optString("targetId"))) {
                count++;
            }
        }
        return count;
    }

    private void normalizeReviews() {
        for (String adId : reviewsByAd.keySet()) {
            JSONArray reviews = reviewsByAd.getJSONArray(adId);
            for (int index = 0; index < reviews.length(); index++) {
                JSONObject review = reviews.getJSONObject(index);
                if (review.optJSONArray("likedUserIds") == null) {
                    JSONArray likedUserIds = new JSONArray();
                    if (review.optBoolean("liked", false)) {
                        likedUserIds.put(UserApiService.CURRENT_USER_ID);
                    }
                    review.put("likedUserIds", likedUserIds);
                }
                refreshReviewState(review);
            }
        }
    }

    private void refreshReviewState(JSONObject review) {
        JSONArray likedUserIds = uniqueUserIds(review.getJSONArray("likedUserIds"));
        review.put("likedUserIds", likedUserIds);
        review.put("likeCount", likedUserIds.length());
        String userId = session.currentUserId();
        review.put("liked", !userId.isBlank() && arrayContains(likedUserIds, userId));
    }

    private JSONObject author(String requestedUserId) {
        String userId = requestedUserId == null || requestedUserId.isBlank()
                || "me".equals(requestedUserId)
                ? session.requireCurrentUserId()
                : requestedUserId;
        JSONObject profileSeed = seedStore.documentCopy("profile.json");
        JSONObject current = profileSeed.getJSONObject("userProfile");
        if (userId.equals(current.getString("userId"))) {
            return current;
        }
        JSONArray users = profileSeed.optJSONArray("users");
        if (users != null) {
            for (int index = 0; index < users.length(); index++) {
                JSONObject user = users.getJSONObject(index);
                if (userId.equals(user.optString("userId"))) {
                    return user;
                }
            }
        }
        throw new IllegalArgumentException("user not found");
    }

    private void persistReviews() {
        seedStore.writeState("reviews.json", new JSONObject()
                .put("reviewsByAd", copy(reviewsByAd))
                .put("comments", new JSONArray(comments.toString())));
    }

    private JSONArray reviewsForAd(String adId) {
        JSONArray reviews = reviewsByAd.optJSONArray(adId);
        return reviews == null ? new JSONArray() : reviews;
    }

    private JSONObject findReview(String reviewId) {
        for (String adId : reviewsByAd.keySet()) {
            JSONArray reviews = reviewsByAd.getJSONArray(adId);
            for (int index = 0; index < reviews.length(); index++) {
                JSONObject review = reviews.getJSONObject(index);
                if (reviewId.equals(review.optString("reviewId"))) {
                    return review;
                }
            }
        }
        return null;
    }

    private static JSONObject objectOrEmpty(String requestBody) {
        String normalized = requestBody == null ? "" : requestBody.trim();
        return normalized.isEmpty() ? new JSONObject() : new JSONObject(normalized);
    }

    private static String requiredContent(JSONObject body) {
        String content = body.optString("content", "").trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        return content;
    }

    private static JSONObject copy(JSONObject source) {
        return new JSONObject(source.toString());
    }

    private static boolean arrayContains(JSONArray source, String value) {
        for (int index = 0; index < source.length(); index++) {
            if (value.equals(source.optString(index))) {
                return true;
            }
        }
        return false;
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

    private static void removeValue(JSONArray source, String value) {
        for (int index = source.length() - 1; index >= 0; index--) {
            if (value.equals(source.optString(index))) {
                source.remove(index);
            }
        }
    }

    private record Query(String rawQuery) {
        static Query from(URI uri) {
            return new Query(uri.getRawQuery());
        }

        String value(String key) {
            if (rawQuery == null || rawQuery.isBlank()) {
                return "";
            }
            String[] pairs = rawQuery.split("&");
            for (String pair : pairs) {
                int separator = pair.indexOf('=');
                String rawKey = separator >= 0 ? pair.substring(0, separator) : pair;
                if (key.equals(decode(rawKey))) {
                    return separator >= 0 ? decode(pair.substring(separator + 1)) : "";
                }
            }
            return "";
        }

        int cursor() {
            return positiveInt(value("cursor"), 0);
        }

        int limit(int defaultValue) {
            return Math.max(1, positiveInt(value("limit"), Math.max(1, defaultValue)));
        }
    }

    private static int positiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
