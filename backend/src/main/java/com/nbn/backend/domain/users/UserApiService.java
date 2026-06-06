package com.nbn.backend.domain.users;

import com.nbn.backend.http.JsonResponse;
import com.nbn.backend.store.JsonSeedStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class UserApiService {
    public static final String CURRENT_USER_ID = "user_current_001";
    public static final String SYSTEM_ADMIN_USER_ID = "1";

    private static final String DEFAULT_AVATAR = "stitch_ui/images/stitch-14.png";

    private final JsonSeedStore seedStore;
    private final UserSession session;
    private final Map<String, JSONObject> profilesById = new LinkedHashMap<>();
    private final JSONArray posts;
    private final JSONArray followers;
    private final JSONArray following;

    public UserApiService(JsonSeedStore seedStore, UserSession session) {
        this.seedStore = seedStore;
        this.session = session;
        JSONObject profileSeed = seedStore.documentCopy("profile.json");
        JSONObject profile = profileSeed.getJSONObject("userProfile");
        profilesById.put(profile.getString("userId"), profile);
        JSONArray users = profileSeed.optJSONArray("users");
        if (users != null) {
            for (int index = 0; index < users.length(); index++) {
                JSONObject user = users.getJSONObject(index);
                profilesById.put(user.getString("userId"), user);
            }
        }
        ensureSessionUser(profile.getString("userId"));
        this.posts = profileSeed.getJSONArray("posts");
        normalizePosts();
        this.followers = profileSeed.getJSONArray("followers");
        this.following = profileSeed.getJSONArray("following");
        normalizeRelations(followers);
        normalizeRelations(following);
        persistProfile();
    }

    public synchronized String createUser(String requestBody) {
        JSONObject body = objectOrEmpty(requestBody);
        String userId = body.optString("userId", "user_" + UUID.randomUUID()).trim();
        if (userId.isBlank()) {
            userId = "user_" + UUID.randomUUID();
        }
        if (profilesById.containsKey(userId)) {
            throw new IllegalArgumentException("user already exists");
        }
        JSONObject profile = new JSONObject();
        profile.put("userId", userId);
        profile.put("nickname", body.optString("nickname", "新用户"));
        profile.put("avatarUrl", body.optString("avatarUrl", DEFAULT_AVATAR));
        profile.put("level", body.optString("level", "Lv.1 新用户"));
        profile.put("bio", body.optString("bio", ""));
        profile.put("stats", emptyStats());
        profile.put("achievements", new JSONArray());
        profilesById.put(userId, profile);
        persistProfile();
        return "{\"userProfile\":" + copy(profile) + "}";
    }

    public synchronized String register(String requestBody) {
        return createUser(requestBody);
    }

    public synchronized String login(String requestBody) {
        JSONObject body = objectOrEmpty(requestBody);
        String userId = body.optString("userId", body.optString("account", "")).trim();
        if (userId.isBlank()) {
            userId = findUserIdByNickname(body.optString("nickname", ""));
        }
        profile(userId);
        session.login(userId);
        persistProfile();
        return authJson(true, userId);
    }

    public synchronized String logout() {
        session.logout();
        persistProfile();
        return authJson(false, "");
    }

    public synchronized String sessionJson() {
        return authJson(session.authenticated(), session.currentUserId());
    }

    public synchronized String meJson() {
        if (!session.authenticated()) {
            return "{\"authenticated\":false,\"userProfile\":null}";
        }
        return "{\"authenticated\":true,\"userProfile\":" + profileJson(currentUserId()) + "}";
    }

    public synchronized String userJson(String userId) {
        return "{\"userProfile\":" + profileJson(userId) + "}";
    }

    public synchronized String patchMe(String requestBody) {
        JSONObject patch = objectOrEmpty(requestBody);
        JSONObject profile = profile(currentUserId());
        copyIfPresent(patch, profile, "nickname");
        copyIfPresent(patch, profile, "avatarUrl");
        copyIfPresent(patch, profile, "level");
        copyIfPresent(patch, profile, "bio");
        if (patch.has("personalInfo")) {
            profile.put("personalInfo", patch.getJSONObject("personalInfo"));
        }
        persistProfile();
        return "{\"userProfile\":" + copy(profile(currentUserId())) + "}";
    }

    public synchronized String statsJson() {
        refreshStatsFor(currentUserId());
        return "{\"stats\":" + copy(profile(currentUserId()).getJSONObject("stats")) + "}";
    }

    public synchronized String achievementsJson() {
        return "{\"achievements\":" + new JSONArray(profile(currentUserId()).getJSONArray("achievements").toString()) + "}";
    }

    public synchronized String postsJson(String userId, String tab) {
        String normalizedUserId = normalizeMe(userId);
        profile(normalizedUserId);
        String normalizedTab = tab == null ? "" : tab.trim();
        JSONArray result = new JSONArray();
        for (int index = 0; index < posts.length(); index++) {
            JSONObject post = posts.getJSONObject(index);
            if (normalizedUserId.equals(post.optString("userId")) && matchesTab(post, normalizedTab)) {
                result.put(copy(post));
            }
        }
        appendInteractionPosts(normalizedUserId, normalizedTab, result);
        return "{\"userId\":\"" + JsonResponse.escape(normalizedUserId) + "\","
                + "\"tab\":\"" + JsonResponse.escape(normalizedTab) + "\","
                + "\"items\":" + result + "}";
    }

    public synchronized String createPost(String requestBody) {
        String userId = currentUserId();
        JSONObject body = objectOrEmpty(requestBody);
        JSONObject post = new JSONObject();
        post.put("postId", body.optString("postId", "post_" + UUID.randomUUID()));
        post.put("userId", userId);
        post.put("author", authorJson(userId));
        post.put("tab", body.optString("tab", "notes"));
        post.put("title", stringOrDefault(body, "title", "未命名笔记"));
        post.put("coverUrl", body.optString("coverUrl", ""));
        post.put("sourceAdId", body.optString("sourceAdId", ""));
        post.put("content", stringOrDefault(body, "content", "暂无正文"));
        post.put("likedUserIds", new JSONArray());
        post.put("collectedUserIds", new JSONArray());
        post.put("sharedUserIds", new JSONArray());
        post.put("likeCount", 0);
        post.put("collectCount", 0);
        post.put("shareCount", 0);
        post.put("timeText", body.optString("timeText", "刚刚"));
        posts.put(post);
        persistProfile();
        return "{\"post\":" + copy(post) + "}";
    }

    public synchronized String patchPost(String postId, String requestBody) {
        JSONObject post = findCurrentUserPost(postId);
        JSONObject patch = objectOrEmpty(requestBody);
        copyIfPresent(patch, post, "tab");
        copyIfPresent(patch, post, "title");
        copyIfPresent(patch, post, "coverUrl");
        copyIfPresent(patch, post, "sourceAdId");
        copyIfPresent(patch, post, "content");
        if (patch.has("likeCount")) {
            post.put("likeCount", patch.optInt("likeCount"));
        }
        persistProfile();
        return "{\"post\":" + copy(post) + "}";
    }

    public synchronized JSONArray allNotePosts() {
        JSONArray result = new JSONArray();
        for (int index = 0; index < posts.length(); index++) {
            JSONObject post = posts.getJSONObject(index);
            if ("notes".equals(post.optString("tab", "notes"))) {
                refreshPostState(post);
                result.put(copy(post));
            }
        }
        return result;
    }

    public synchronized JSONObject postAsFeedItem(String postId) {
        JSONObject post = findPost(postId);
        if (post == null) {
            return null;
        }
        refreshPostState(post);
        JSONObject author = post.optJSONObject("author");
        return new JSONObject()
                .put("adId", post.optString("postId"))
                .put("contentType", "USER_POST")
                .put("adType", "USER_POST")
                .put("featured", true)
                .put("channelIds", new JSONArray().put("featured"))
                .put("title", post.optString("title"))
                .put("description", post.optString("content"))
                .put("brand", author == null ? "" : author.optString("nickname"))
                .put("category", "用户笔记")
                .put("publishTime", post.optString("timeText", "刚刚"))
                .put("cover", new JSONObject().put("localAssetName", post.optString("coverUrl", DEFAULT_AVATAR)))
                .put("creator", author == null ? authorJson(post.optString("userId")) : copy(author))
                .put("tags", new JSONArray().put(new JSONObject().put("id", "tag_user_note").put("name", "用户笔记")))
                .put("stats", postStats(post))
                .put("interactionState", postInteractionState(post))
                .put("likedUserIds", new JSONArray(post.getJSONArray("likedUserIds").toString()))
                .put("collectedUserIds", new JSONArray(post.getJSONArray("collectedUserIds").toString()))
                .put("sharedUserIds", new JSONArray(post.getJSONArray("sharedUserIds").toString()));
    }

    public synchronized String applyPostInteraction(String postId, String commandName) {
        JSONObject post = findPost(postId);
        if (post == null) {
            return null;
        }
        switch (commandName) {
            case "LIKE" -> setPostRelation(post, "likedUserIds", true);
            case "UNLIKE" -> setPostRelation(post, "likedUserIds", false);
            case "COLLECT" -> setPostRelation(post, "collectedUserIds", true);
            case "UNCOLLECT" -> setPostRelation(post, "collectedUserIds", false);
            case "SHARE" -> setPostRelation(post, "sharedUserIds", true);
            case "CLICK", "EXPOSURE" -> {
            }
            default -> throw new IllegalArgumentException("unsupported post interaction");
        }
        refreshPostState(post);
        persistProfile();
        return new JSONObject()
                .put("adId", postId)
                .put("currentUserId", currentUserId())
                .put("stats", postStats(post))
                .put("interactionState", postInteractionState(post))
                .put("likedUserIds", new JSONArray(post.getJSONArray("likedUserIds").toString()))
                .put("collectedUserIds", new JSONArray(post.getJSONArray("collectedUserIds").toString()))
                .toString();
    }

    public synchronized void incrementPostCommentCount(String postId, int delta) {
        JSONObject post = findPost(postId);
        if (post == null) {
            return;
        }
        post.put("commentCount", Math.max(0, post.optInt("commentCount", 0) + delta));
        persistProfile();
    }

    public synchronized void setPostCommentCount(String postId, int count) {
        JSONObject post = findPost(postId);
        if (post == null) {
            return;
        }
        post.put("commentCount", Math.max(0, count));
        persistProfile();
    }

    public synchronized String deletePost(String postId) {
        String userId = currentUserId();
        for (int index = 0; index < posts.length(); index++) {
            JSONObject post = posts.getJSONObject(index);
            if (postId.equals(post.optString("postId")) && userId.equals(post.optString("userId"))) {
                JSONObject removed = (JSONObject) posts.remove(index);
                persistProfile();
                return "{\"deleted\":true,\"post\":" + copy(removed) + "}";
            }
        }
        throw new IllegalArgumentException("post not found");
    }

    public synchronized String followersJson(String userId) {
        String normalizedUserId = normalizeMe(userId);
        profile(normalizedUserId);
        return "{\"userId\":\"" + JsonResponse.escape(normalizedUserId) + "\",\"items\":"
                + filteredRelations(followers, "targetUserId", normalizedUserId) + "}";
    }

    public synchronized String followingJson(String userId) {
        String normalizedUserId = normalizeMe(userId);
        profile(normalizedUserId);
        return "{\"userId\":\"" + JsonResponse.escape(normalizedUserId) + "\",\"items\":"
                + filteredRelations(following, "userId", normalizedUserId) + "}";
    }

    public synchronized String follow(String targetUserId) {
        String userId = currentUserId();
        String normalizedTarget = normalizeMe(targetUserId);
        if (userId.equals(normalizedTarget)) {
            throw new IllegalArgumentException("cannot follow self");
        }
        profile(normalizedTarget);
        JSONObject relation = upsertRelation(
                following,
                "rel_following_" + relationKey(userId, normalizedTarget),
                userId,
                normalizedTarget,
                "following",
                true
        );
        upsertRelation(
                followers,
                "rel_follower_" + relationKey(userId, normalizedTarget),
                userId,
                normalizedTarget,
                "follower",
                true
        );
        persistProfile();
        return relationJson(relation, normalizedTarget);
    }

    public synchronized String unfollow(String targetUserId) {
        String userId = currentUserId();
        String normalizedTarget = normalizeMe(targetUserId);
        JSONObject relation = findRelation(following, userId, normalizedTarget);
        if (relation == null) {
            throw new IllegalArgumentException("follow relation not found");
        }
        relation.put("following", false);
        JSONObject followerRelation = findRelation(followers, userId, normalizedTarget);
        if (followerRelation != null) {
            followerRelation.put("following", false);
        }
        persistProfile();
        return relationJson(relation, normalizedTarget);
    }

    private String profileJson(String userId) {
        String normalizedUserId = normalizeMe(userId);
        refreshStatsFor(normalizedUserId);
        return copy(profile(normalizedUserId)).toString();
    }

    private JSONObject profile(String userId) {
        JSONObject profile = profilesById.get(normalizeMe(userId));
        if (profile == null) {
            throw new IllegalArgumentException("user not found");
        }
        return profile;
    }

    private JSONObject findCurrentUserPost(String postId) {
        String userId = currentUserId();
        for (int index = 0; index < posts.length(); index++) {
            JSONObject post = posts.getJSONObject(index);
            if (postId.equals(post.optString("postId")) && userId.equals(post.optString("userId"))) {
                return post;
            }
        }
        throw new IllegalArgumentException("post not found");
    }

    private JSONObject findPost(String postId) {
        for (int index = 0; index < posts.length(); index++) {
            JSONObject post = posts.getJSONObject(index);
            if (postId.equals(post.optString("postId"))) {
                return post;
            }
        }
        return null;
    }

    private void refreshAllStats() {
        for (String userId : profilesById.keySet()) {
            refreshStatsFor(userId);
        }
    }

    private void refreshStatsFor(String userId) {
        String normalizedUserId = normalizeMe(userId);
        JSONObject profile = profilesById.get(normalizedUserId);
        if (profile == null) {
            return;
        }
        profile.put("stats", new JSONObject()
                .put("likedAndCollectedCount", likedAndCollectedCount(normalizedUserId))
                .put("followingCount", activeRelationCount(following, "userId", normalizedUserId))
                .put("followerCount", activeRelationCount(followers, "targetUserId", normalizedUserId))
                .put("postCount", notePostCount(normalizedUserId)));
    }

    private int notePostCount(String userId) {
        int count = 0;
        for (int index = 0; index < posts.length(); index++) {
            JSONObject post = posts.getJSONObject(index);
            if (userId.equals(post.optString("userId")) && "notes".equals(post.optString("tab", "notes"))) {
                count++;
            }
        }
        return count;
    }

    private int activeRelationCount(JSONArray relations, String key, String userId) {
        int count = 0;
        for (int index = 0; index < relations.length(); index++) {
            JSONObject relation = relations.getJSONObject(index);
            if (userId.equals(relation.optString(key)) && relation.optBoolean("following", false)) {
                count++;
            }
        }
        return count;
    }

    private int likedAndCollectedCount(String userId) {
        JSONObject homeFeed = seedStore.documentCopy("home_feed.json");
        JSONArray items = homeFeed.getJSONObject("page").getJSONArray("items");
        int count = 0;
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.getJSONObject(index);
            if (arrayContains(item.optJSONArray("likedUserIds"), userId)) {
                count++;
            }
            if (arrayContains(item.optJSONArray("collectedUserIds"), userId)) {
                count++;
            }
        }
        for (int index = 0; index < posts.length(); index++) {
            JSONObject post = posts.getJSONObject(index);
            if (arrayContains(post.optJSONArray("likedUserIds"), userId)) {
                count++;
            }
            if (arrayContains(post.optJSONArray("collectedUserIds"), userId)) {
                count++;
            }
        }
        return count;
    }

    private void appendInteractionPosts(String userId, String tab, JSONArray result) {
        if (!"collections".equals(tab) && !"liked".equals(tab)) {
            return;
        }
        String relationKey = "collections".equals(tab) ? "collectedUserIds" : "likedUserIds";
        JSONObject homeFeed = seedStore.documentCopy("home_feed.json");
        JSONArray items = homeFeed.getJSONObject("page").getJSONArray("items");
        for (int index = 0; index < items.length(); index++) {
            JSONObject ad = items.getJSONObject(index);
            if (arrayContains(ad.optJSONArray(relationKey), userId)) {
                result.put(adAsProfilePost(ad, userId, tab));
            }
        }
        for (int index = 0; index < posts.length(); index++) {
            JSONObject post = posts.getJSONObject(index);
            if (!userId.equals(post.optString("userId")) && arrayContains(post.optJSONArray(relationKey), userId)) {
                JSONObject copy = copy(post);
                copy.put("tab", tab);
                result.put(copy);
            }
        }
    }

    private JSONObject adAsProfilePost(JSONObject ad, String userId, String tab) {
        JSONObject cover = ad.optJSONObject("cover");
        JSONObject stats = ad.optJSONObject("stats");
        return new JSONObject()
                .put("postId", tab + "_" + ad.optString("adId"))
                .put("userId", userId)
                .put("tab", tab)
                .put("title", ad.optString("title"))
                .put("coverUrl", cover == null ? "" : cover.optString("localAssetName", cover.optString("url", "")))
                .put("sourceAdId", ad.optString("adId"))
                .put("content", ad.optString("description"))
                .put("likeCount", stats == null ? 0 : stats.optInt("likeCount", 0))
                .put("timeText", "liked".equals(tab) ? "来自点赞" : "来自收藏");
    }

    private JSONArray filteredRelations(JSONArray relations, String key, String userId) {
        JSONArray result = new JSONArray();
        for (int index = 0; index < relations.length(); index++) {
            JSONObject relation = relations.getJSONObject(index);
            if (userId.equals(relation.optString(key)) && relation.optBoolean("following", false)) {
                result.put(copy(relation));
            }
        }
        return result;
    }

    private JSONObject upsertRelation(
            JSONArray relations,
            String relationId,
            String userId,
            String targetUserId,
            String relationType,
            boolean active
    ) {
        JSONObject relation = findRelation(relations, userId, targetUserId);
        if (relation == null) {
            relation = new JSONObject();
            relation.put("relationId", relationId);
            relation.put("userId", userId);
            relation.put("targetUserId", targetUserId);
            relation.put("relationType", relationType);
            relations.put(relation);
        }
        relation.put("following", active);
        return relation;
    }

    private JSONObject findRelation(JSONArray relations, String userId, String targetUserId) {
        for (int index = 0; index < relations.length(); index++) {
            JSONObject relation = relations.getJSONObject(index);
            if (userId.equals(relation.optString("userId"))
                    && targetUserId.equals(relation.optString("targetUserId"))) {
                return relation;
            }
        }
        return null;
    }

    private String relationJson(JSONObject relation, String targetUserId) {
        return new JSONObject()
                .put("relation", copy(relation))
                .put("currentUserStats", copy(profile(currentUserId()).getJSONObject("stats")))
                .put("targetUserStats", copy(profile(targetUserId).getJSONObject("stats")))
                .toString();
    }

    private JSONObject authorJson(String userId) {
        JSONObject profile = profile(userId);
        return new JSONObject()
                .put("userId", profile.optString("userId"))
                .put("nickname", profile.optString("nickname"))
                .put("avatarUrl", profile.optString("avatarUrl"))
                .put("verified", profile.optBoolean("verified", false))
                .put("bio", profile.optString("bio"));
    }

    private void persistProfile() {
        refreshAllStats();
        String storedProfileId = storedProfileId();
        JSONArray users = new JSONArray();
        for (JSONObject profile : profilesById.values()) {
            if (!storedProfileId.equals(profile.optString("userId"))) {
                users.put(copy(profile));
            }
        }
        seedStore.writeState("profile.json", new JSONObject()
                .put("currentUserId", session.currentUserId())
                .put("userProfile", copy(profile(storedProfileId)))
                .put("users", users)
                .put("posts", new JSONArray(posts.toString()))
                .put("followers", new JSONArray(followers.toString()))
                .put("following", new JSONArray(following.toString())));
    }

    private void ensureSessionUser(String fallbackUserId) {
        String currentUserId = session.persistentCurrentUserId();
        if (!currentUserId.isBlank() && profilesById.containsKey(currentUserId)) {
            return;
        }
        if (fallbackUserId != null && profilesById.containsKey(fallbackUserId)) {
            session.login(fallbackUserId);
        }
    }

    private String storedProfileId() {
        String currentUserId = session.persistentCurrentUserId();
        if (!currentUserId.isBlank() && profilesById.containsKey(currentUserId)) {
            return currentUserId;
        }
        if (profilesById.containsKey(CURRENT_USER_ID)) {
            return CURRENT_USER_ID;
        }
        return profilesById.keySet().iterator().next();
    }

    private void normalizePosts() {
        for (int index = 0; index < posts.length(); index++) {
            JSONObject post = posts.getJSONObject(index);
            if (post.optJSONObject("author") == null && !post.optString("userId").isBlank()) {
                post.put("author", authorJson(post.optString("userId")));
            }
            ensurePostArray(post, "likedUserIds");
            ensurePostArray(post, "collectedUserIds");
            ensurePostArray(post, "sharedUserIds");
            refreshPostState(post);
        }
    }

    private void ensurePostArray(JSONObject post, String key) {
        if (post.optJSONArray(key) == null) {
            post.put(key, new JSONArray());
        }
    }

    private void setPostRelation(JSONObject post, String arrayKey, boolean active) {
        String userId = currentUserId();
        JSONArray userIds = post.getJSONArray(arrayKey);
        boolean contains = arrayContains(userIds, userId);
        if (active && !contains) {
            userIds.put(userId);
            return;
        }
        if (!active && contains) {
            removeValue(userIds, userId);
        }
    }

    private void refreshPostState(JSONObject post) {
        ensurePostArray(post, "likedUserIds");
        ensurePostArray(post, "collectedUserIds");
        ensurePostArray(post, "sharedUserIds");
        post.put("likedUserIds", uniqueUserIds(post.getJSONArray("likedUserIds")));
        post.put("collectedUserIds", uniqueUserIds(post.getJSONArray("collectedUserIds")));
        post.put("sharedUserIds", uniqueUserIds(post.getJSONArray("sharedUserIds")));
        post.put("likeCount", post.getJSONArray("likedUserIds").length());
        post.put("collectCount", post.getJSONArray("collectedUserIds").length());
        post.put("shareCount", post.getJSONArray("sharedUserIds").length());
    }

    private JSONObject postStats(JSONObject post) {
        refreshPostState(post);
        return new JSONObject()
                .put("likeCount", post.optInt("likeCount", 0))
                .put("collectCount", post.optInt("collectCount", 0))
                .put("shareCount", post.optInt("shareCount", 0))
                .put("commentCount", post.optInt("commentCount", 0));
    }

    private JSONObject postInteractionState(JSONObject post) {
        String userId = session.currentUserId();
        return new JSONObject()
                .put("liked", !userId.isBlank() && arrayContains(post.optJSONArray("likedUserIds"), userId))
                .put("collected", !userId.isBlank() && arrayContains(post.optJSONArray("collectedUserIds"), userId))
                .put("shared", !userId.isBlank() && arrayContains(post.optJSONArray("sharedUserIds"), userId))
                .put("followingCreator", !userId.isBlank() && isFollowing(userId, post.optString("userId")));
    }

    public synchronized boolean isFollowing(String userId, String targetUserId) {
        return findRelation(following, userId, targetUserId) != null
                && findRelation(following, userId, targetUserId).optBoolean("following", false);
    }

    private String authJson(boolean authenticated, String userId) {
        JSONObject data = new JSONObject()
                .put("authenticated", authenticated)
                .put("currentUserId", userId == null ? "" : userId);
        if (authenticated) {
            data.put("userProfile", copy(profile(userId)));
        }
        return data.toString();
    }

    private String findUserIdByNickname(String nickname) {
        String normalized = nickname == null ? "" : nickname.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("user not found");
        }
        for (JSONObject profile : profilesById.values()) {
            if (normalized.equals(profile.optString("nickname"))) {
                return profile.optString("userId");
            }
        }
        throw new IllegalArgumentException("user not found");
    }

    private String currentUserId() {
        return session.requireCurrentUserId();
    }

    private static JSONObject emptyStats() {
        return new JSONObject()
                .put("likedAndCollectedCount", 0)
                .put("followingCount", 0)
                .put("followerCount", 0)
                .put("postCount", 0);
    }

    private static void normalizeRelations(JSONArray relations) {
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < relations.length(); index++) {
            JSONObject relation = relations.getJSONObject(index);
            String key = relation.optString("userId") + "->" + relation.optString("targetUserId")
                    + ":" + relation.optString("relationType");
            if (!seen.add(key)) {
                relations.remove(index);
                index--;
            }
        }
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

    private static boolean matchesTab(JSONObject post, String tab) {
        return tab == null || tab.isEmpty() || tab.equals(post.optString("tab"));
    }

    private String normalizeMe(String userId) {
        String normalized = userId == null ? "" : userId.trim();
        return normalized.isEmpty() || "me".equals(normalized) ? currentUserId() : normalized;
    }

    private static String relationKey(String userId, String targetUserId) {
        return (userId + "_" + targetUserId).replaceAll("[^A-Za-z0-9]+", "_");
    }

    private static boolean arrayContains(JSONArray array, String value) {
        if (array == null) {
            return false;
        }
        for (int index = 0; index < array.length(); index++) {
            if (value.equals(array.optString(index))) {
                return true;
            }
        }
        return false;
    }

    private static void copyIfPresent(JSONObject source, JSONObject target, String key) {
        if (source.has(key)) {
            target.put(key, source.opt(key));
        }
    }

    private static void removeValue(JSONArray source, String value) {
        for (int index = source.length() - 1; index >= 0; index--) {
            if (value.equals(source.optString(index))) {
                source.remove(index);
            }
        }
    }

    private static JSONObject objectOrEmpty(String requestBody) {
        String normalized = requestBody == null ? "" : requestBody.trim();
        return normalized.isEmpty() ? new JSONObject() : new JSONObject(normalized);
    }

    private static String stringOrDefault(JSONObject source, String key, String fallback) {
        String value = source.optString(key, "").trim();
        return value.isEmpty() ? fallback : value;
    }

    private static JSONObject copy(JSONObject source) {
        return new JSONObject(source.toString());
    }
}
