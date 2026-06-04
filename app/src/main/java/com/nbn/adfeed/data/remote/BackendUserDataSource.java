package com.nbn.adfeed.data.remote;

import com.nbn.adfeed.data.model.stitch.StitchProfileModels;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BackendUserDataSource {
    public interface Transport {
        String get(String path) throws RemoteAdException;

        String post(String path, String body) throws RemoteAdException;

        String patch(String path, String body) throws RemoteAdException;

        String delete(String path) throws RemoteAdException;
    }

    private final Transport transport;

    public BackendUserDataSource(Transport transport) {
        this.transport = transport == null ? defaultTransport() : transport;
    }

    public static BackendUserDataSource defaultDataSource() {
        return new BackendUserDataSource(defaultTransport());
    }

    public StitchProfileModels.UserProfile createUser(String userId, String nickname, String bio)
            throws RemoteAdException {
        JSONObject body = object(
                "userId", safe(userId),
                "nickname", safe(nickname),
                "bio", safe(bio)
        );
        JSONObject data = dataFromEnvelope(transport.post("/v1/users", body.toString()));
        return parseUserProfile(requiredObject(data, "userProfile"));
    }

    public StitchProfileModels.UserProfile getMe() throws RemoteAdException {
        JSONObject data = dataFromEnvelope(transport.get("/v1/users/me"));
        return parseUserProfile(requiredObject(data, "userProfile"));
    }

    public StitchProfileModels.UserProfile patchMe(String nickname, String bio, JSONObject personalInfo)
            throws RemoteAdException {
        JSONObject body = object(
                "nickname", safe(nickname),
                "bio", safe(bio)
        );
        if (personalInfo != null) {
            put(body, "personalInfo", personalInfo);
        }
        JSONObject data = dataFromEnvelope(transport.patch("/v1/users/me", body.toString()));
        return parseUserProfile(requiredObject(data, "userProfile"));
    }

    public List<StitchProfileModels.ProfilePost> posts(String userId, String tab) throws RemoteAdException {
        String path = "/v1/users/" + encode(userId) + "/posts?tab=" + encode(tab);
        JSONObject data = dataFromEnvelope(transport.get(path));
        return parsePosts(data.optJSONArray("items"));
    }

    public StitchProfileModels.ProfilePost createPost(
            String postId,
            String tab,
            String title,
            String content,
            String sourceAdId
    ) throws RemoteAdException {
        JSONObject body = object(
                "postId", safe(postId),
                "tab", safe(tab),
                "title", safe(title),
                "content", safe(content),
                "sourceAdId", safe(sourceAdId)
        );
        JSONObject data = dataFromEnvelope(transport.post("/v1/users/me/posts", body.toString()));
        return parsePost(requiredObject(data, "post"));
    }

    public StitchProfileModels.ProfilePost patchPost(String postId, String title, String content)
            throws RemoteAdException {
        JSONObject body = object(
                "title", safe(title),
                "content", safe(content)
        );
        JSONObject data = dataFromEnvelope(transport.patch(
                "/v1/users/me/posts/" + encode(postId),
                body.toString()
        ));
        return parsePost(requiredObject(data, "post"));
    }

    public boolean deletePost(String postId) throws RemoteAdException {
        JSONObject data = dataFromEnvelope(transport.delete("/v1/users/me/posts/" + encode(postId)));
        return data.optBoolean("deleted", false);
    }

    public List<StitchProfileModels.FollowRelation> followers(String userId) throws RemoteAdException {
        JSONObject data = dataFromEnvelope(transport.get("/v1/users/" + encode(userId) + "/followers"));
        return parseRelations(data.optJSONArray("items"));
    }

    public List<StitchProfileModels.FollowRelation> following(String userId) throws RemoteAdException {
        JSONObject data = dataFromEnvelope(transport.get("/v1/users/" + encode(userId) + "/following"));
        return parseRelations(data.optJSONArray("items"));
    }

    public StitchProfileModels.FollowRelation follow(String targetUserId) throws RemoteAdException {
        JSONObject data = dataFromEnvelope(transport.post(
                "/v1/users/" + encode(targetUserId) + "/follow",
                ""
        ));
        return parseRelation(requiredObject(data, "relation"));
    }

    public StitchProfileModels.FollowRelation unfollow(String targetUserId) throws RemoteAdException {
        JSONObject data = dataFromEnvelope(transport.delete("/v1/users/" + encode(targetUserId) + "/follow"));
        return parseRelation(requiredObject(data, "relation"));
    }

    private static JSONObject dataFromEnvelope(String responseBody) throws RemoteAdException {
        try {
            JSONObject envelope = new JSONObject(responseBody);
            if (!"OK".equals(envelope.optString("code"))) {
                throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, envelope.optString("message"));
            }
            JSONObject data = envelope.optJSONObject("data");
            if (data == null) {
                throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, "Backend user response missing data");
            }
            return data;
        } catch (JSONException exception) {
            throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, exception.getMessage());
        }
    }

    private static JSONObject object(Object... keyValues) throws RemoteAdException {
        JSONObject json = new JSONObject();
        for (int index = 0; index < keyValues.length; index += 2) {
            put(json, String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return json;
    }

    private static void put(JSONObject json, String key, Object value) throws RemoteAdException {
        try {
            json.put(key, value);
        } catch (JSONException exception) {
            throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, exception.getMessage());
        }
    }

    private static JSONObject requiredObject(JSONObject data, String key) throws RemoteAdException {
        JSONObject value = data.optJSONObject(key);
        if (value == null) {
            throw new RemoteAdException(
                    RemoteAdException.Reason.INVALID_RESPONSE,
                    "Backend user response missing " + key
            );
        }
        return value;
    }

    private static StitchProfileModels.UserProfile parseUserProfile(JSONObject profile) {
        JSONObject stats = profile.optJSONObject("stats");
        return new StitchProfileModels.UserProfile(
                profile.optString("userId"),
                profile.optString("nickname"),
                profile.optString("avatarUrl"),
                profile.optString("level"),
                profile.optString("bio"),
                new StitchProfileModels.ProfileStats(
                        stats == null ? 0 : stats.optInt("likedAndCollectedCount", 0),
                        stats == null ? 0 : stats.optInt("followingCount", 0),
                        stats == null ? 0 : stats.optInt("followerCount", 0),
                        stats == null ? 0 : stats.optInt("postCount", 0)
                ),
                parseAchievements(profile.optJSONArray("achievements"))
        );
    }

    private static List<StitchProfileModels.Achievement> parseAchievements(JSONArray achievementsJson) {
        if (achievementsJson == null) {
            return Collections.emptyList();
        }
        List<StitchProfileModels.Achievement> achievements = new ArrayList<>();
        for (int index = 0; index < achievementsJson.length(); index++) {
            JSONObject achievement = achievementsJson.optJSONObject(index);
            if (achievement != null) {
                achievements.add(new StitchProfileModels.Achievement(
                        achievement.optString("achievementId"),
                        achievement.optString("title"),
                        achievement.optString("description"),
                        achievement.optString("icon"),
                        achievement.optBoolean("unlocked", false)
                ));
            }
        }
        return achievements;
    }

    private static List<StitchProfileModels.ProfilePost> parsePosts(JSONArray postsJson) {
        if (postsJson == null) {
            return Collections.emptyList();
        }
        List<StitchProfileModels.ProfilePost> posts = new ArrayList<>();
        for (int index = 0; index < postsJson.length(); index++) {
            JSONObject post = postsJson.optJSONObject(index);
            if (post != null) {
                posts.add(parsePost(post));
            }
        }
        return posts;
    }

    private static StitchProfileModels.ProfilePost parsePost(JSONObject post) {
        return new StitchProfileModels.ProfilePost(
                post.optString("postId"),
                post.optString("tab"),
                post.optString("title"),
                post.optString("coverUrl"),
                post.optString("sourceAdId"),
                post.optInt("likeCount", 0),
                post.optString("timeText")
        );
    }

    private static List<StitchProfileModels.FollowRelation> parseRelations(JSONArray relationsJson) {
        if (relationsJson == null) {
            return Collections.emptyList();
        }
        List<StitchProfileModels.FollowRelation> relations = new ArrayList<>();
        for (int index = 0; index < relationsJson.length(); index++) {
            JSONObject relation = relationsJson.optJSONObject(index);
            if (relation != null) {
                relations.add(parseRelation(relation));
            }
        }
        return relations;
    }

    private static StitchProfileModels.FollowRelation parseRelation(JSONObject relation) {
        return new StitchProfileModels.FollowRelation(
                relation.optString("relationId"),
                relation.optString("userId"),
                relation.optString("targetUserId"),
                relation.optString("relationType"),
                relation.optBoolean("following", false)
        );
    }

    private static Transport defaultTransport() {
        return new Transport() {
            @Override
            public String get(String path) throws RemoteAdException {
                return request(path, "", "GET");
            }

            @Override
            public String post(String path, String body) throws RemoteAdException {
                return request(path, body, "POST");
            }

            @Override
            public String patch(String path, String body) throws RemoteAdException {
                return request(path, body, "PATCH");
            }

            @Override
            public String delete(String path) throws RemoteAdException {
                return request(path, "", "DELETE");
            }

            private String request(String path, String body, String method) throws RemoteAdException {
                RemoteAdException lastException = null;
                for (BackendConfig candidate : BackendConfig.defaultCandidates()) {
                    try {
                        HttpApiClient client = new HttpApiClient(candidate);
                        if ("POST".equals(method)) {
                            return client.post(path, body);
                        }
                        if ("PATCH".equals(method)) {
                            return client.patch(path, body);
                        }
                        if ("DELETE".equals(method)) {
                            return client.delete(path);
                        }
                        return client.get(path);
                    } catch (RemoteAdException exception) {
                        lastException = exception;
                    }
                }
                throw lastException == null
                        ? new RemoteAdException(RemoteAdException.Reason.NETWORK, "Backend user API unavailable")
                        : lastException;
            }
        };
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException exception) {
            return "";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
