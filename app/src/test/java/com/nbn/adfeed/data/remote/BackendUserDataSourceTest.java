package com.nbn.adfeed.data.remote;

import com.nbn.adfeed.data.model.stitch.StitchProfileModels;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class BackendUserDataSourceTest {
    @Test
    public void userClientCallsProfilePostAndFollowRoutes() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        BackendUserDataSource dataSource = new BackendUserDataSource(transport);

        StitchProfileModels.UserProfile createdUser = dataSource.createUser(
                "user_client_001",
                "客户端用户",
                "from android"
        );
        StitchProfileModels.UserProfile me = dataSource.getMe();
        StitchProfileModels.UserProfile patchedMe = dataSource.patchMe(
                "改名用户",
                "patched",
                new JSONObject().put("city", "Shanghai")
        );
        StitchProfileModels.ProfilePost createdPost = dataSource.createPost(
                "post_client_001",
                "notes",
                "客户端笔记",
                "content",
                "ad_001"
        );
        StitchProfileModels.ProfilePost patchedPost = dataSource.patchPost(
                "post_client_001",
                "编辑后的笔记",
                "patched content"
        );
        List<StitchProfileModels.ProfilePost> notes = dataSource.posts("me", "notes");
        StitchProfileModels.FollowRelation follow = dataSource.follow("creator_tech_03");
        StitchProfileModels.FollowRelation unfollow = dataSource.unfollow("creator_tech_03");
        boolean deleted = dataSource.deletePost("post_client_001");

        assertEquals("user_client_001", createdUser.userId);
        assertEquals("灵感收集员", me.nickname);
        assertEquals("改名用户", patchedMe.nickname);
        assertEquals("post_client_001", createdPost.postId);
        assertEquals("编辑后的笔记", patchedPost.title);
        assertEquals(1, notes.size());
        assertTrue(follow.following);
        assertEquals(false, unfollow.following);
        assertTrue(deleted);
        assertEquals("POST /v1/users", transport.calls.get(0));
        assertEquals("GET /v1/users/me", transport.calls.get(1));
        assertEquals("PATCH /v1/users/me", transport.calls.get(2));
        assertTrue(transport.requestBodies.get(2).contains("\"city\":\"Shanghai\""));
        assertEquals("POST /v1/users/me/posts", transport.calls.get(3));
        assertEquals("PATCH /v1/users/me/posts/post_client_001", transport.calls.get(4));
        assertEquals("GET /v1/users/me/posts?tab=notes", transport.calls.get(5));
        assertEquals("POST /v1/users/creator_tech_03/follow", transport.calls.get(6));
        assertEquals("DELETE /v1/users/creator_tech_03/follow", transport.calls.get(7));
        assertEquals("DELETE /v1/users/me/posts/post_client_001", transport.calls.get(8));
    }

    @Test(expected = RemoteAdException.class)
    public void nonOkEnvelopeThrowsRemoteException() throws Exception {
        BackendUserDataSource dataSource = new BackendUserDataSource(new RecordingTransport() {
            @Override
            public String get(String path) {
                return "{\"requestId\":\"req-test\",\"code\":\"NOT_FOUND\",\"message\":\"missing\",\"data\":null}";
            }
        });

        dataSource.getMe();
    }

    private static class RecordingTransport implements BackendUserDataSource.Transport {
        private final java.util.List<String> calls = new java.util.ArrayList<>();
        private final java.util.List<String> requestBodies = new java.util.ArrayList<>();

        @Override
        public String get(String path) {
            calls.add("GET " + path);
            requestBodies.add("");
            if (path.contains("/posts")) {
                return ok("{\"userId\":\"user_me_001\",\"tab\":\"notes\",\"items\":["
                        + "{\"postId\":\"post_note_001\",\"tab\":\"notes\",\"title\":\"笔记\","
                        + "\"coverUrl\":\"cover\",\"sourceAdId\":\"ad_001\",\"likeCount\":3,\"timeText\":\"今天\"}"
                        + "]}");
            }
            return ok("{\"userProfile\":" + profileJson("user_me_001", "灵感收集员") + "}");
        }

        @Override
        public String post(String path, String body) {
            calls.add("POST " + path);
            requestBodies.add(body == null ? "" : body);
            if ("/v1/users".equals(path)) {
                return ok("{\"userProfile\":" + profileJson("user_client_001", "客户端用户") + "}");
            }
            if (path.endsWith("/posts")) {
                return ok("{\"post\":{\"postId\":\"post_client_001\",\"tab\":\"notes\","
                        + "\"title\":\"客户端笔记\",\"coverUrl\":\"\",\"sourceAdId\":\"ad_001\","
                        + "\"likeCount\":0,\"timeText\":\"刚刚\"}}");
            }
            return ok("{\"relation\":{\"relationId\":\"rel_client_001\",\"userId\":\"user_me_001\","
                    + "\"targetUserId\":\"creator_tech_03\",\"relationType\":\"following\",\"following\":true}}");
        }

        @Override
        public String patch(String path, String body) {
            calls.add("PATCH " + path);
            requestBodies.add(body == null ? "" : body);
            if (path.contains("/posts/")) {
                return ok("{\"post\":{\"postId\":\"post_client_001\",\"tab\":\"notes\","
                        + "\"title\":\"编辑后的笔记\",\"coverUrl\":\"\",\"sourceAdId\":\"ad_001\","
                        + "\"likeCount\":0,\"timeText\":\"刚刚\"}}");
            }
            return ok("{\"userProfile\":" + profileJson("user_me_001", "改名用户") + "}");
        }

        @Override
        public String delete(String path) {
            calls.add("DELETE " + path);
            requestBodies.add("");
            if (path.contains("/posts/")) {
                return ok("{\"deleted\":true,\"post\":{\"postId\":\"post_client_001\"}}");
            }
            return ok("{\"relation\":{\"relationId\":\"rel_client_001\",\"userId\":\"user_me_001\","
                    + "\"targetUserId\":\"creator_tech_03\",\"relationType\":\"following\",\"following\":false}}");
        }

        private static String ok(String dataJson) {
            return "{\"requestId\":\"req-test\",\"code\":\"OK\",\"message\":\"ok\",\"data\":" + dataJson + "}";
        }

        private static String profileJson(String userId, String nickname) {
            return "{\"userId\":\"" + userId + "\",\"nickname\":\"" + nickname + "\","
                    + "\"avatarUrl\":\"avatar\",\"level\":\"Lv.1\",\"bio\":\"bio\","
                    + "\"stats\":{\"likedAndCollectedCount\":1,\"followingCount\":2,"
                    + "\"followerCount\":3,\"postCount\":4},"
                    + "\"achievements\":[{\"achievementId\":\"ach_1\",\"title\":\"成就\","
                    + "\"description\":\"desc\",\"icon\":\"star\",\"unlocked\":true}]}";
        }
    }
}
