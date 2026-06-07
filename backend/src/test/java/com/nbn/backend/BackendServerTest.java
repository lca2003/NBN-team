package com.nbn.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

public final class BackendServerTest {
    private BackendServer server;

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void healthReturnsUnifiedEnvelope() throws Exception {
        server = BackendServer.create(0);
        server.start();

        HttpResult result = request("GET", "/health", "req-health-test");

        assertEquals(200, result.statusCode);
        assertEquals("req-health-test", result.requestId);
        assertTrue(result.body.contains("\"requestId\":\"req-health-test\""));
        assertTrue(result.body.contains("\"code\":\"OK\""));
        assertTrue(result.body.contains("\"message\":\"ok\""));
        assertTrue(result.body.contains("\"service\":\"nbn-backend\""));
        assertTrue(result.body.contains("\"version\":\"0.1.0\""));
        assertTrue(result.body.contains("\"seed\":{"));
        assertTrue(result.body.contains("\"state\":{"));
        assertTrue(result.body.contains("\"persistenceEnabled\":false"));
        assertTrue(result.body.contains("\"expectedCount\":7"));
        assertTrue(result.body.contains("\"loadedCount\":7"));
        assertTrue(result.body.contains("\"domains\":{"));
        assertTrue(result.body.contains("\"feedItems\":30"));
    }

    @Test
    public void unknownRouteReturnsUnifiedNotFoundError() throws Exception {
        server = BackendServer.create(0);
        server.start();

        HttpResult result = request("GET", "/v1/unknown", "req-not-found-test");

        assertEquals(404, result.statusCode);
        assertEquals("req-not-found-test", result.requestId);
        assertTrue(result.body.contains("\"code\":\"NOT_FOUND\""));
        assertTrue(result.body.contains("\"message\":\"resource not found\""));
        assertTrue(result.body.contains("\"data\":null"));
    }

    @Test
    public void unsupportedHealthMethodReturnsUnifiedMethodError() throws Exception {
        server = BackendServer.create(0);
        server.start();

        HttpResult result = request("POST", "/health", "req-method-test");

        assertEquals(405, result.statusCode);
        assertEquals("req-method-test", result.requestId);
        assertTrue(result.body.contains("\"code\":\"METHOD_NOT_ALLOWED\""));
        assertTrue(result.body.contains("\"data\":null"));
    }

    @Test
    public void malformedUserCreateJsonReturnsBadRequestEnvelope() throws Exception {
        server = BackendServer.create(0);
        server.start();

        HttpResult result = request("POST", "/v1/users", "req-user-malformed-json", "{");
        JSONObject body = new JSONObject(result.body);

        assertEquals(400, result.statusCode);
        assertEquals("req-user-malformed-json", result.requestId);
        assertEquals("req-user-malformed-json", body.getString("requestId"));
        assertEquals("BAD_REQUEST", body.getString("code"));
        assertEquals("bad request", body.getString("message"));
        assertTrue(body.isNull("data"));
    }

    @Test
    public void seedStatusLoadsAllPhaseTwoSeedResources() throws Exception {
        server = BackendServer.create(0);

        assertEquals(7, server.seedStatus().expectedCount());
        assertEquals(7, server.seedStatus().loadedCount());
        assertEquals("loaded", server.seedStatus().status());
        assertEquals(30, server.seedStore().feedItemCount());
        assertEquals(30, server.seedStore().detailCount());
        assertEquals(6, server.seedStore().searchSuggestionCount());
    }

    @Test
    public void feedRoutesReturnSeedBackedData() throws Exception {
        server = BackendServer.create(0);
        server.start();

        HttpResult channels = request("GET", "/v1/feed/channels", "req-feed-channels");
        HttpResult feed = request("GET", "/v1/feed?channel=featured&limit=2", "req-feed-page");

        assertEquals(200, channels.statusCode);
        assertTrue(channels.body.contains("\"channels\""));
        assertTrue(channels.body.contains("\"featured\""));
        assertEquals(200, feed.statusCode);
        JSONObject data = new JSONObject(feed.body).getJSONObject("data");
        assertEquals("featured", data.getString("channel"));
        assertEquals(30, data.getInt("totalCount"));
        assertEquals(2, data.getJSONArray("items").length());
        assertTrue(data.getBoolean("hasMore"));
    }

    @Test
    public void adDetailAndRelatedRoutesReturnSeedBackedData() throws Exception {
        server = BackendServer.create(0);
        server.start();

        HttpResult ad = request("GET", "/v1/ads/ad_001", "req-ad");
        HttpResult detail = request("GET", "/v1/ads/ad_001/detail", "req-detail");
        HttpResult related = request("GET", "/v1/ads/ad_001/related", "req-related");

        assertEquals(200, ad.statusCode);
        assertEquals("ad_001", new JSONObject(ad.body)
                .getJSONObject("data")
                .getJSONObject("ad")
                .getString("adId"));
        assertEquals(200, detail.statusCode);
        assertTrue(detail.body.contains("\"aiDeepInsight\""));
        assertEquals(200, related.statusCode);
        assertTrue(related.body.contains("\"itemId\":\"ad_009\""));
    }

    @Test
    public void interactionRoutesMutateServerSideState() throws Exception {
        server = BackendServer.create(0);
        server.start();

        JSONObject initialAd = adData(request("GET", "/v1/ads/ad_001", "req-initial"));
        assertEquals("1", initialAd.getJSONObject("creator").getString("userId"));
        assertEquals(2, initialAd.getJSONObject("stats").getInt("likeCount"));
        assertEquals(false, initialAd.getJSONObject("interactionState").getBoolean("liked"));

        JSONObject liked = data(request("POST", "/v1/ads/ad_001/like", "req-like"));
        assertEquals("user_current_001", liked.getString("currentUserId"));
        assertEquals(3, liked.getJSONObject("stats").getInt("likeCount"));
        assertTrue(liked.getJSONObject("interactionState").getBoolean("liked"));
        JSONObject duplicateLike = data(request("POST", "/v1/ads/ad_001/like", "req-like-repeat"));
        assertEquals(3, duplicateLike.getJSONObject("stats").getInt("likeCount"));

        JSONObject collected = data(request("POST", "/v1/ads/ad_001/collect", "req-collect"));
        assertEquals(2, collected.getJSONObject("stats").getInt("collectCount"));
        assertTrue(collected.getJSONObject("interactionState").getBoolean("collected"));

        JSONObject shared = data(request("POST", "/v1/ads/ad_001/share", "req-share"));
        assertEquals(2, shared.getJSONObject("stats").getInt("shareCount"));
        assertTrue(shared.getJSONObject("interactionState").getBoolean("shared"));

        JSONObject clicked = data(request("POST", "/v1/ads/ad_001/click", "req-click"));
        assertEquals(269, clicked.getJSONObject("stats").getInt("clickCount"));
        JSONArray stitchDetails = data(request(
                "GET",
                "/v1/stitch/pages/detail",
                "req-stitch-detail-after-click"
        )).getJSONObject("details").getJSONArray("details");
        JSONObject stitchDetail = null;
        for (int index = 0; index < stitchDetails.length(); index++) {
            JSONObject candidate = stitchDetails.getJSONObject(index);
            if ("ad_001".equals(candidate.getString("adId"))) {
                stitchDetail = candidate;
                break;
            }
        }
        assertNotNull(stitchDetail);
        assertEquals("269", stitchDetail.getJSONObject("stats").getString("clickText"));

        JSONObject exposed = data(request("POST", "/v1/ads/ad_001/exposure", "req-exposure"));
        assertEquals(1681, exposed.getJSONObject("stats").getInt("exposureCount"));

        JSONObject unliked = data(request("DELETE", "/v1/ads/ad_001/like", "req-unlike"));
        assertEquals(2, unliked.getJSONObject("stats").getInt("likeCount"));
        assertEquals(false, unliked.getJSONObject("interactionState").getBoolean("liked"));
    }

    @Test
    public void concurrentInteractionsDeduplicateRelationsAndKeepRequestUserIsolation() throws Exception {
        server = BackendServer.create(0);
        server.start();

        List<String> users = new ArrayList<>();
        users.add("user_pkg11_A");
        users.add("user_pkg11_B");
        users.add("user_pkg11_C");
        users.add("user_pkg11_D");
        users.add("user_pkg11_E");
        for (String userId : users) {
            data(request(
                    "POST",
                    "/v1/users",
                    "req-pkg11-create-" + userId,
                    "{\"userId\":\"" + userId + "\",\"nickname\":\"" + userId + "\"}"
            ));
        }

        JSONObject ad = adData(requestAsUser("GET", "/v1/ads/ad_001", "req-pkg11-admin-ad", "user_pkg11_A"));
        assertEquals("1", ad.getJSONObject("creator").getString("userId"));

        JSONObject post = data(requestAsUser(
                "POST",
                "/v1/users/me/posts",
                "req-pkg11-create-post-a",
                "user_pkg11_A",
                "{\"postId\":\"post_pkg11_A\",\"title\":\"PKG11 isolation\",\"content\":\"request scoped session\"}"
        )).getJSONObject("post");
        assertEquals("user_pkg11_A", post.getString("userId"));
        assertEquals("user_pkg11_A", post.getJSONObject("author").getString("userId"));
        assertTrue(!"1".equals(post.getJSONObject("author").getString("userId")));

        JSONObject session = data(request("GET", "/v1/auth/session", "req-pkg11-session-after-header"));
        assertEquals("user_current_001", session.getString("currentUserId"));

        List<CompletableFuture<JSONObject>> duplicatePostLikes = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            int requestIndex = index;
            duplicatePostLikes.add(CompletableFuture.supplyAsync(() -> data(uncheckedRequestAsUser(
                    "POST",
                    "/v1/ads/post_pkg11_A/like",
                    "req-pkg11-dup-post-like-" + requestIndex,
                    "user_pkg11_B"
            ))));
        }
        for (CompletableFuture<JSONObject> future : duplicatePostLikes) {
            assertEquals("user_pkg11_B", future.get().getString("currentUserId"));
        }
        JSONObject likedPost = data(requestAsUser(
                "GET",
                "/v1/ads/post_pkg11_A",
                "req-pkg11-post-after-duplicate-like",
                "user_pkg11_B"
        )).getJSONObject("ad");
        assertNoDuplicates(likedPost.getJSONArray("likedUserIds"));
        assertEquals(1, likedPost.getJSONObject("stats").getInt("likeCount"));
        assertTrue(likedPost.getJSONObject("interactionState").getBoolean("liked"));

        List<CompletableFuture<JSONObject>> multiUserAdLikes = new ArrayList<>();
        for (String userId : users) {
            multiUserAdLikes.add(CompletableFuture.supplyAsync(() -> data(uncheckedRequestAsUser(
                    "POST",
                    "/v1/ads/ad_001/like",
                    "req-pkg11-like-" + userId,
                    userId
            ))));
        }
        for (int index = 0; index < multiUserAdLikes.size(); index++) {
            assertEquals(users.get(index), multiUserAdLikes.get(index).get().getString("currentUserId"));
        }
        JSONObject finalAd = adData(requestAsUser("GET", "/v1/ads/ad_001", "req-pkg11-final-ad", "user_pkg11_C"));
        JSONArray likedUserIds = finalAd.getJSONArray("likedUserIds");
        assertNoDuplicates(likedUserIds);
        assertEquals(likedUserIds.length(), finalAd.getJSONObject("stats").getInt("likeCount"));
        for (String userId : users) {
            assertTrue(likedUserIds.toString().contains(userId));
        }

        List<CompletableFuture<JSONObject>> duplicateFollows = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            int requestIndex = index;
            duplicateFollows.add(CompletableFuture.supplyAsync(() -> data(uncheckedRequestAsUser(
                    "POST",
                    "/v1/users/user_pkg11_A/follow",
                    "req-pkg11-follow-dup-" + requestIndex,
                    "user_pkg11_B"
            ))));
        }
        for (CompletableFuture<JSONObject> future : duplicateFollows) {
            assertEquals("user_pkg11_B", future.get().getJSONObject("relation").getString("userId"));
        }
        JSONArray following = data(requestAsUser(
                "GET",
                "/v1/users/user_pkg11_B/following",
                "req-pkg11-following-b",
                "user_pkg11_B"
        )).getJSONArray("items");
        assertEquals(1, following.length());
        assertEquals("user_pkg11_A", following.getJSONObject(0).getString("targetUserId"));
    }

    @Test
    public void unknownAdReturnsNotFoundEnvelope() throws Exception {
        server = BackendServer.create(0);
        server.start();

        HttpResult result = request("GET", "/v1/ads/ad_missing", "req-missing-ad");

        assertEquals(404, result.statusCode);
        assertTrue(result.body.contains("\"code\":\"NOT_FOUND\""));
        assertTrue(result.body.contains("\"data\":null"));
    }

    @Test
    public void stitchPageRouteReturnsWebViewPayloadFromBackend() throws Exception {
        server = BackendServer.create(0);
        server.start();

        HttpResult home = request("GET", "/v1/stitch/pages/home", "req-stitch-home");
        HttpResult detail = request("GET", "/v1/stitch/pages/detail", "req-stitch-detail");
        HttpResult profile = request("GET", "/v1/stitch/pages/profile", "req-stitch-profile");

        assertEquals(200, home.statusCode);
        JSONObject homeData = data(home);
        JSONArray homeItems = homeData.getJSONObject("homeFeed").getJSONObject("page").getJSONArray("items");
        assertTrue(homeItems.length() >= 5);
        assertTrue(homeItems.toString().contains("\"contentType\":\"USER_POST\""));
        assertTrue(homeData.has("appConfig"));
        assertEquals(200, detail.statusCode);
        JSONObject detailData = data(detail);
        assertEquals(30, detailData.getJSONObject("details").getJSONArray("details").length());
        assertTrue(detailData.has("reviews"));
        assertTrue(detailData.has("appConfig"));

        assertEquals(200, profile.statusCode);
        JSONObject profileData = data(profile).getJSONObject("profile");
        JSONObject userProfile = profileData.getJSONObject("userProfile");
        JSONArray posts = profileData.getJSONArray("posts");
        assertEquals("user_current_001", userProfile.getString("userId"));
        assertEquals(0, userProfile.getJSONObject("stats").getInt("followingCount"));
        assertEquals(0, userProfile.getJSONObject("stats").getInt("followerCount"));
        assertEquals(0, userProfile.getJSONObject("stats").getInt("postCount"));
        assertEquals(0, posts.length());
        assertTrue(profileData.has("users"));
    }

    @Test
    public void stitchProfilePayloadIncludesInteractionTabsAndDerivedStats() throws Exception {
        server = BackendServer.create(0);
        server.start();

        data(request("POST", "/v1/auth/logout", "req-profile-tabs-logout"));
        data(request(
                "POST",
                "/v1/auth/register",
                "req-profile-tabs-register-a",
                "{\"userId\":\"user_profile_tabs_A\",\"nickname\":\"Profile Tabs A\"}"
        ));
        data(request(
                "POST",
                "/v1/auth/login",
                "req-profile-tabs-login-a",
                "{\"userId\":\"user_profile_tabs_A\"}"
        ));
        data(request(
                "POST",
                "/v1/users/me/posts",
                "req-profile-tabs-post-a",
                "{\"postId\":\"post_profile_tabs_A\",\"tab\":\"notes\",\"title\":\"Profile tab post\",\"content\":\"visible in B liked and collections\"}"
        ));
        data(request("POST", "/v1/auth/logout", "req-profile-tabs-logout-a"));
        data(request(
                "POST",
                "/v1/auth/register",
                "req-profile-tabs-register-b",
                "{\"userId\":\"user_profile_tabs_B\",\"nickname\":\"Profile Tabs B\"}"
        ));
        data(request(
                "POST",
                "/v1/auth/login",
                "req-profile-tabs-login-b",
                "{\"userId\":\"user_profile_tabs_B\"}"
        ));
        data(request("POST", "/v1/ads/ad_001/like", "req-profile-tabs-like-ad"));
        data(request("POST", "/v1/ads/ad_001/collect", "req-profile-tabs-collect-ad"));
        data(request("POST", "/v1/ads/post_profile_tabs_A/like", "req-profile-tabs-like-post"));
        data(request("POST", "/v1/ads/post_profile_tabs_A/collect", "req-profile-tabs-collect-post"));

        JSONObject profile = data(request("GET", "/v1/stitch/pages/profile", "req-profile-tabs-payload"))
                .getJSONObject("profile");

        assertEquals("user_profile_tabs_B", profile.getJSONObject("userProfile").getString("userId"));
        assertEquals(4, profile.getJSONObject("userProfile").getJSONObject("stats")
                .getInt("likedAndCollectedCount"));
        assertEquals(0, profile.getJSONArray("posts").length());
        assertEquals(2, profile.getJSONArray("liked").length());
        assertEquals(2, profile.getJSONArray("collections").length());
        assertTrue(profile.getJSONArray("liked").toString().contains("ad_001"));
        assertTrue(profile.getJSONArray("liked").toString().contains("post_profile_tabs_A"));
        assertTrue(profile.getJSONArray("collections").toString().contains("ad_001"));
        assertTrue(profile.getJSONArray("collections").toString().contains("post_profile_tabs_A"));
    }

    @Test
    public void userRoutesCreateEditPostsAndFollowRelations() throws Exception {
        server = BackendServer.create(0);
        server.start();

        JSONObject createdUser = data(request(
                "POST",
                "/v1/users",
                "req-user-create",
                "{\"userId\":\"user_test_001\",\"nickname\":\"Test User\",\"bio\":\"created from api\"}"
        )).getJSONObject("userProfile");
        assertEquals("user_test_001", createdUser.getString("userId"));
        assertEquals("Test User", createdUser.getString("nickname"));
        assertEquals(0, createdUser.getJSONObject("stats").getInt("followingCount"));
        assertEquals(0, createdUser.getJSONObject("stats").getInt("followerCount"));
        assertEquals(0, createdUser.getJSONObject("stats").getInt("postCount"));

        JSONObject me = data(request("GET", "/v1/users/me", "req-user-me")).getJSONObject("userProfile");
        assertEquals("user_current_001", me.getString("userId"));
        assertEquals(0, me.getJSONObject("stats").getInt("followingCount"));
        assertEquals(0, me.getJSONObject("stats").getInt("followerCount"));
        assertEquals(0, me.getJSONObject("stats").getInt("postCount"));

        JSONObject patchedMe = data(request(
                "PATCH",
                "/v1/users/me",
                "req-user-patch",
                "{\"nickname\":\"后端资料用户\",\"bio\":\"资料已由后端 PATCH 更新\",\"personalInfo\":{\"city\":\"Shanghai\"}}"
        )).getJSONObject("userProfile");
        assertEquals("后端资料用户", patchedMe.getString("nickname"));
        assertEquals("Shanghai", patchedMe.getJSONObject("personalInfo").getString("city"));

        JSONObject stats = data(request("GET", "/v1/users/me/stats", "req-user-stats")).getJSONObject("stats");
        assertEquals(0, stats.getInt("postCount"));
        assertEquals(0, stats.getInt("followingCount"));
        assertEquals(0, stats.getInt("followerCount"));
        assertEquals(0, data(request("GET", "/v1/users/me/achievements", "req-user-achievements"))
                .getJSONArray("achievements").length());

        JSONObject notePost = data(request(
                "POST",
                "/v1/users/me/posts",
                "req-post-create",
                "{\"postId\":\"post_test_001\",\"tab\":\"notes\",\"title\":\"后端创建笔记\",\"sourceAdId\":\"ad_001\"}"
        )).getJSONObject("post");
        assertEquals("post_test_001", notePost.getString("postId"));

        JSONObject patchedPost = data(request(
                "PATCH",
                "/v1/users/me/posts/post_test_001",
                "req-post-patch",
                "{\"title\":\"后端编辑后的笔记\"}"
        )).getJSONObject("post");
        assertEquals("后端编辑后的笔记", patchedPost.getString("title"));

        JSONObject notes = data(request("GET", "/v1/users/me/posts?tab=notes", "req-post-notes"));
        assertEquals(1, notes.getJSONArray("items").length());

        JSONObject follow = data(request("POST", "/v1/users/user_runner_001/follow", "req-follow"));
        assertTrue(follow.getJSONObject("relation").getBoolean("following"));
        JSONObject following = data(request("GET", "/v1/users/me/following", "req-following"));
        assertEquals(1, following.getJSONArray("items").length());
        JSONObject followers = data(request("GET", "/v1/users/user_runner_001/followers", "req-followers"));
        assertEquals(1, followers.getJSONArray("items").length());
        JSONObject unfollow = data(request("DELETE", "/v1/users/user_runner_001/follow", "req-unfollow"));
        assertEquals(false, unfollow.getJSONObject("relation").getBoolean("following"));

        JSONObject deleted = data(request("DELETE", "/v1/users/me/posts/post_test_001", "req-post-delete"));
        assertTrue(deleted.getBoolean("deleted"));
    }

    @Test
    public void userPostCreateUsesDefaultsForBlankTitleAndContent() throws Exception {
        server = BackendServer.create(0);
        server.start();

        JSONObject post = data(request(
                "POST",
                "/v1/users/me/posts",
                "req-post-create-defaults",
                "{\"postId\":\"post_default_001\",\"tab\":\"notes\",\"title\":\"   \",\"content\":\"\"}"
        )).getJSONObject("post");

        assertEquals("未命名笔记", post.getString("title"));
        assertEquals("暂无正文", post.getString("content"));
    }

    @Test
    public void authUserPostInteractionAndMessagingFlowUsesRealAccounts() throws Exception {
        server = BackendServer.create(0);
        server.start();

        assertEquals(false, data(request("POST", "/v1/auth/logout", "req-auth-logout-initial"))
                .getBoolean("authenticated"));
        data(request(
                "POST",
                "/v1/auth/register",
                "req-register-a",
                "{\"userId\":\"user_e2e_A\",\"nickname\":\"链路账号A\",\"bio\":\"A bio\"}"
        ));
        JSONObject loginA = data(request(
                "POST",
                "/v1/auth/login",
                "req-login-a",
                "{\"userId\":\"user_e2e_A\"}"
        ));
        assertEquals("user_e2e_A", loginA.getString("currentUserId"));

        JSONObject patchedA = data(request(
                "PATCH",
                "/v1/users/me",
                "req-patch-a",
                "{\"nickname\":\"链路作者A\",\"bio\":\"已编辑资料\",\"avatarUrl\":\"stitch_ui/images/stitch-13.png\"}"
        )).getJSONObject("userProfile");
        assertEquals("链路作者A", patchedA.getString("nickname"));

        JSONObject postA = data(request(
                "POST",
                "/v1/users/me/posts",
                "req-post-a",
                "{\"postId\":\"post_e2e_A\",\"tab\":\"notes\",\"title\":\"A 发布的真实笔记\",\"content\":\"这条笔记要被 B 在首页刷到\"}"
        )).getJSONObject("post");
        assertEquals("user_e2e_A", postA.getString("userId"));

        data(request("POST", "/v1/auth/logout", "req-logout-a"));
        data(request(
                "POST",
                "/v1/auth/register",
                "req-register-b",
                "{\"userId\":\"user_e2e_B\",\"nickname\":\"链路账号B\"}"
        ));
        JSONObject loginB = data(request(
                "POST",
                "/v1/auth/login",
                "req-login-b",
                "{\"userId\":\"user_e2e_B\"}"
        ));
        assertEquals("user_e2e_B", loginB.getString("currentUserId"));

        JSONArray homeItems = data(request("GET", "/v1/stitch/pages/home", "req-home-b"))
                .getJSONObject("homeFeed")
                .getJSONObject("page")
                .getJSONArray("items");
        assertTrue(homeItems.toString().contains("post_e2e_A"));

        JSONObject likedPost = data(request("POST", "/v1/ads/post_e2e_A/like", "req-like-post"));
        assertEquals("user_e2e_B", likedPost.getString("currentUserId"));
        assertTrue(likedPost.getJSONObject("interactionState").getBoolean("liked"));

        JSONObject collectedPost = data(request("POST", "/v1/ads/post_e2e_A/collect", "req-collect-post"));
        assertTrue(collectedPost.getJSONObject("interactionState").getBoolean("collected"));

        JSONObject comment = data(request(
                "POST",
                "/v1/comments",
                "req-comment-post",
                "{\"commentId\":\"comment_e2e_B\",\"targetType\":\"post\",\"targetId\":\"post_e2e_A\",\"content\":\"B 的真实评论\"}"
        )).getJSONObject("comment");
        assertEquals("user_e2e_B", comment.getString("userId"));
        assertEquals(1, adData(request("GET", "/v1/ads/post_e2e_A", "req-post-comment-count"))
                .getJSONObject("stats")
                .getInt("commentCount"));

        JSONObject follow = data(request("POST", "/v1/users/user_e2e_A/follow", "req-follow-a"));
        assertTrue(follow.getJSONObject("relation").getBoolean("following"));
        assertEquals(1, data(request("GET", "/v1/users/user_e2e_A/followers", "req-a-followers"))
                .getJSONArray("items").length());

        JSONObject message = data(request(
                "POST",
                "/v1/conversations/conv_direct_e2e/messages",
                "req-direct-message",
                "{\"targetUserId\":\"user_e2e_A\",\"messageId\":\"dm_e2e_B_to_A\",\"content\":\"B 给 A 的真实私信\"}"
        )).getJSONObject("message");
        assertEquals("user_e2e_B", message.getString("senderUserId"));
        assertEquals("conv_direct_e2e", message.getString("conversationId"));
        JSONObject secondMessage = data(request(
                "POST",
                "/v1/conversations/conv_direct_e2e_duplicate/messages",
                "req-direct-message-reuse",
                "{\"targetUserId\":\"user_e2e_A\",\"messageId\":\"dm_e2e_B_to_A_2\",\"content\":\"B 第二次给 A 的真实私信\"}"
        )).getJSONObject("message");
        assertEquals("conv_direct_e2e", secondMessage.getString("conversationId"));
        assertTrue(data(request(
                "GET",
                "/v1/conversations/conv_direct_e2e/messages",
                "req-direct-message-list"
        )).getJSONArray("messages").toString().contains("dm_e2e_B_to_A"));
    }

    @Test
    public void aiRoutesSearchSummarizeTagAndPersistSessions() throws Exception {
        server = BackendServer.create(0);
        server.start();

        JSONObject suggestions = data(request("GET", "/v1/ai/search/suggestions", "req-ai-suggestions"));
        assertTrue(suggestions.getJSONArray("suggestions").length() >= 4);

        JSONObject search = data(request(
                "POST",
                "/v1/ai/search",
                "req-ai-search",
                "{\"sessionId\":\"search_session_test_001\",\"query\":\"学生党通勤跑鞋\",\"context\":\"unit_test\"}"
        ));
        assertEquals("search_session_test_001", search.getJSONObject("session").getString("sessionId"));
        assertTrue(search.getJSONArray("results").length() >= 1);
        assertEquals("ad_001", search.getJSONArray("results").getJSONObject(0).getString("adId"));
        assertEquals("rule_fallback", search.getJSONObject("provider").getString("source"));
        assertEquals("AI_PROVIDER_NOT_CONFIGURED", search.getJSONObject("fallback").getString("fallbackReason"));

        JSONObject appended = data(request(
                "POST",
                "/v1/ai/search/sessions/search_session_test_001/messages",
                "req-ai-message",
                "{\"content\":\"预算 300 以内\"}"
        ));
        assertTrue(appended.getJSONArray("messages").length() >= 4);

        JSONObject session = data(request(
                "GET",
                "/v1/ai/search/sessions/search_session_test_001",
                "req-ai-session"
        ));
        assertEquals("search_session_test_001", session.getJSONObject("session").getString("sessionId"));

        JSONObject summary = data(request("POST", "/v1/ai/ads/ad_001/summary", "req-ai-summary"));
        assertEquals("ad_001", summary.getString("adId"));
        assertEquals("rule_fallback", summary.getString("source"));
        assertTrue(summary.getString("summary").contains("学生党"));

        JSONObject tags = data(request("POST", "/v1/ai/ads/ad_001/tags", "req-ai-tags"));
        assertEquals("ad_001", tags.getString("adId"));
        assertTrue(tags.getJSONArray("tags").length() >= 3);

        JSONObject rerank = data(request(
                "POST",
                "/v1/ai/ads/rerank",
                "req-ai-rerank",
                "{\"query\":\"通勤\",\"adIds\":[\"ad_001\",\"ad_003\"]}"
        ));
        assertEquals("rule_fallback", rerank.getString("source"));
        assertTrue(rerank.getJSONArray("items").length() >= 1);
    }

    @Test
    public void reviewAndCommentRoutesReadCreateAndMutateState() throws Exception {
        server = BackendServer.create(0);
        server.start();

        JSONObject reviews = data(request("GET", "/v1/ads/ad_001/reviews?limit=1", "req-review-list"));
        assertEquals("ad_001", reviews.getString("adId"));
        assertEquals(2, reviews.getInt("totalCount"));
        assertEquals(1, reviews.getJSONArray("items").length());
        assertTrue(reviews.getBoolean("hasMore"));

        JSONObject createdReview = data(request(
                "POST",
                "/v1/ads/ad_001/reviews",
                "req-review-create",
                "{\"reviewId\":\"review_backend_001\",\"nickname\":\"真实后端用户\",\"content\":\"后端新增评价\"}"
        )).getJSONObject("review");
        assertEquals("review_backend_001", createdReview.getString("reviewId"));
        assertEquals("user_current_001", createdReview.getString("userId"));

        HttpResult blankReview = request(
                "POST",
                "/v1/ads/ad_001/reviews",
                "req-review-blank",
                "{\"reviewId\":\"review_backend_blank\",\"content\":\"   \"}"
        );
        assertEquals(400, blankReview.statusCode);
        assertEquals("BAD_REQUEST", new JSONObject(blankReview.body).getString("code"));

        JSONObject likedReview = data(request(
                "POST",
                "/v1/reviews/review_backend_001/like",
                "req-review-like"
        )).getJSONObject("review");
        assertTrue(likedReview.getBoolean("liked"));
        assertEquals(1, likedReview.getInt("likeCount"));
        JSONObject unlikedReview = data(request(
                "DELETE",
                "/v1/reviews/review_backend_001/like",
                "req-review-unlike"
        )).getJSONObject("review");
        assertEquals(false, unlikedReview.getBoolean("liked"));
        assertEquals(0, unlikedReview.getInt("likeCount"));

        JSONObject comments = data(request(
                "GET",
                "/v1/comments?targetType=ad&targetId=ad_001",
                "req-comment-list"
        ));
        assertEquals("ad_001", comments.getString("targetId"));
        assertTrue(comments.getJSONArray("items").length() >= 1);

        JSONObject createdComment = data(request(
                "POST",
                "/v1/comments",
                "req-comment-create",
                "{\"commentId\":\"comment_backend_001\",\"targetType\":\"ad\",\"targetId\":\"ad_001\",\"content\":\"后端新增评论\"}"
        )).getJSONObject("comment");
        assertEquals("comment_backend_001", createdComment.getString("commentId"));
        assertEquals("user_current_001", createdComment.getString("userId"));
        assertEquals(2, adData(request("GET", "/v1/ads/ad_001", "req-comment-count"))
                .getJSONObject("stats")
                .getInt("commentCount"));

        HttpResult blankComment = request(
                "POST",
                "/v1/comments",
                "req-comment-blank",
                "{\"commentId\":\"comment_backend_blank\",\"targetType\":\"ad\",\"targetId\":\"ad_001\",\"content\":\"  \"}"
        );
        assertEquals(400, blankComment.statusCode);
        assertEquals("BAD_REQUEST", new JSONObject(blankComment.body).getString("code"));
        assertEquals(2, adData(request("GET", "/v1/ads/ad_001", "req-comment-count-after-blank"))
                .getJSONObject("stats")
                .getInt("commentCount"));

        String directDetail = data(request("GET", "/v1/ads/ad_001/detail", "req-detail-feedback"))
                .getJSONObject("detail")
                .toString();
        assertTrue(directDetail.contains("review_backend_001"));
        assertTrue(directDetail.contains("comment_backend_001"));

        String stitchedDetail = data(request("GET", "/v1/stitch/pages/detail", "req-stitch-detail-feedback"))
                .getJSONObject("details")
                .toString();
        assertTrue(stitchedDetail.contains("review_backend_001"));
        assertTrue(stitchedDetail.contains("comment_backend_001"));

        JSONObject deletedComment = data(request(
                "DELETE",
                "/v1/comments/comment_backend_001",
                "req-comment-delete"
        ));
        assertTrue(deletedComment.getBoolean("deleted"));
        assertEquals(1, adData(request("GET", "/v1/ads/ad_001", "req-comment-count-restored"))
                .getJSONObject("stats")
                .getInt("commentCount"));
    }

    @Test
    public void commerceMerchantAndCheckoutRoutesReturnSeedBackedState() throws Exception {
        server = BackendServer.create(0);
        server.start();

        JSONObject commerce = data(request("GET", "/v1/ads/ad_001/commerce", "req-commerce"));
        assertEquals("ad_001", commerce.getString("adId"));
        assertEquals("merchant_nbn_sports", commerce.getJSONObject("merchant").getString("merchantId"));
        assertEquals("offer_ad_001", commerce.getJSONObject("offer").getString("offerId"));

        JSONObject merchant = data(request(
                "GET",
                "/v1/merchants/merchant_nbn_sports",
                "req-merchant"
        )).getJSONObject("merchant");
        assertEquals("NBN Sports 官方旗舰", merchant.getString("name"));

        JSONObject nearby = data(request(
                "GET",
                "/v1/merchants/merchant_nbn_sports/nearby",
                "req-nearby"
        ));
        assertTrue(nearby.getJSONArray("items").length() >= 2);

        JSONObject checkout = data(request(
                "POST",
                "/v1/orders/checkout-intent",
                "req-checkout",
                "{\"adId\":\"ad_001\",\"quantity\":2}"
        ));
        assertEquals("ad_001", checkout.getString("adId"));
        assertEquals("offer_ad_001", checkout.getString("offerId"));
        assertEquals("CREATED", checkout.getString("status"));
    }

    @Test
    public void messageRoutesReadAppendAndClearUnreadState() throws Exception {
        server = BackendServer.create(0);
        server.start();

        JSONObject summary = data(request(
                "GET",
                "/v1/notifications/summary",
                "req-notification-summary"
        )).getJSONObject("notificationSummary");
        assertEquals(4, summary.getInt("totalUnreadCount"));

        JSONObject stitchSummary = data(request(
                "GET",
                "/v1/stitch/pages/messages",
                "req-stitch-messages-summary"
        )).getJSONObject("messages").getJSONObject("notificationSummary");
        assertEquals(summary.getInt("likeUnreadCount"), stitchSummary.getInt("likeUnreadCount"));
        assertEquals(summary.getInt("collectUnreadCount"), stitchSummary.getInt("collectUnreadCount"));
        assertEquals(summary.getInt("followerUnreadCount"), stitchSummary.getInt("followerUnreadCount"));
        assertEquals(summary.getInt("commentUnreadCount"), stitchSummary.getInt("commentUnreadCount"));
        assertEquals(summary.getInt("totalUnreadCount"), stitchSummary.getInt("totalUnreadCount"));

        JSONObject likeNotifications = data(request(
                "GET",
                "/v1/notifications?type=like&limit=1",
                "req-notification-list"
        ));
        assertEquals("like", likeNotifications.getString("type"));
        assertEquals(1, likeNotifications.getJSONArray("notifications").length());

        JSONObject readLike = data(request(
                "POST",
                "/v1/notifications/read",
                "req-notification-read",
                "{\"notificationIds\":[\"nt_like_001\"]}"
        ));
        assertEquals(1, readLike.getInt("readCount"));

        JSONObject conversations = data(request("GET", "/v1/conversations?limit=1", "req-conversations"));
        assertEquals(1, conversations.getJSONArray("conversations").length());

        JSONObject createdMessage = data(request(
                "POST",
                "/v1/conversations/conv_ai_assistant/messages",
                "req-message-create",
                "{\"messageId\":\"dm_backend_001\",\"content\":\"这条消息来自真实后端\"}"
        )).getJSONObject("message");
        assertEquals("dm_backend_001", createdMessage.getString("messageId"));

        JSONObject messages = data(request(
                "GET",
                "/v1/conversations/conv_ai_assistant/messages",
                "req-message-list"
        ));
        assertTrue(messages.getJSONArray("messages").length() >= 2);

        JSONObject direct = data(request(
                "POST",
                "/v1/conversations/conv_backend_direct/messages",
                "req-direct-create",
                "{\"targetUserId\":\"user_rider_005\",\"messageId\":\"dm_backend_direct\",\"content\":\"真实后端私信\"}"
        ));
        assertEquals("conv_backend_direct", direct.getJSONObject("conversation").getString("conversationId"));
        assertEquals("dm_backend_direct", direct.getJSONObject("message").getString("messageId"));
        assertEquals(400, request(
                "POST",
                "/v1/conversations/conv_backend_bad/messages",
                "req-direct-bad-user",
                "{\"targetUserId\":\"user_missing_backend\",\"messageId\":\"dm_backend_bad\",\"content\":\"bad\"}"
        ).statusCode);
        assertEquals(400, requestAsUser(
                "POST",
                "/v1/conversations/conv_ai_assistant/messages",
                "req-message-unknown-sender",
                "user_missing_sender",
                "{\"messageId\":\"dm_unknown_sender\",\"content\":\"bad\"}"
        ).statusCode);
        assertEquals(400, requestAsUser(
                "POST",
                "/v1/notifications/read-all",
                "req-notification-read-all-unknown-user",
                "user_missing_sender",
                "{}"
        ).statusCode);

        JSONObject digest = data(request("GET", "/v1/ai-assistant/digest", "req-digest"));
        assertTrue(digest.getJSONObject("aiAssistantDigest").getString("summary").contains("新增互动"));

        JSONObject readAll = data(request("POST", "/v1/notifications/read-all", "req-notification-read-all"));
        assertEquals(0, readAll.getJSONObject("notificationSummary").getInt("totalUnreadCount"));

        JSONObject stitchAfterReadAll = data(request(
                "GET",
                "/v1/stitch/pages/messages",
                "req-stitch-messages-after-read-all"
        )).getJSONObject("messages").getJSONObject("notificationSummary");
        assertEquals(0, stitchAfterReadAll.getInt("totalUnreadCount"));
    }

    @Test
    public void platformConfigAssetAndAnalyticsRoutesRecordBatches() throws Exception {
        server = BackendServer.create(0);
        server.start();

        JSONObject config = data(request("GET", "/v1/config/app", "req-config"));
        assertTrue(config.getJSONObject("remoteConfig").getBoolean("aiEnabled"));

        JSONObject manifest = data(request("GET", "/v1/assets/manifest", "req-assets"));
        assertTrue(manifest.getJSONArray("assetManifest").length() >= 3);

        JSONObject homeContent = data(request("GET", "/v1/design-content/home", "req-design-home"));
        assertEquals(30, homeContent.getJSONObject("homeFeed").getJSONObject("page").getJSONArray("items").length());

        JSONObject events = data(request(
                "POST",
                "/v1/events/batch",
                "req-events",
                "{\"events\":[{\"eventType\":\"AD_CLICK\",\"adId\":\"ad_001\"}]}"
        ));
        assertEquals(1, events.getInt("acceptedCount"));

        JSONObject exposures = data(request(
                "POST",
                "/v1/exposures/batch",
                "req-exposures",
                "{\"exposures\":[{\"adId\":\"ad_001\",\"visiblePercent\":80,\"dwellMillis\":1500}]}"
        ));
        assertEquals(1, exposures.getInt("acceptedCount"));
        assertEquals(1, exposures.getInt("validExposureCount"));

        JSONObject analytics = data(request("GET", "/v1/analytics/summary", "req-analytics"));
        assertEquals(1, analytics.getInt("totalEventCount"));
        assertEquals(1, analytics.getInt("validExposureCount"));
    }

    @Test
    public void jsonStatePersistenceRestoresMutationsAcrossBackendRestart() throws Exception {
        Path stateDirectory = Files.createTempDirectory("nbn-backend-state-test");
        server = BackendServer.create(0, stateDirectory);
        server.start();

        JSONObject liked = data(request("POST", "/v1/ads/ad_001/like", "req-persist-like"));
        assertTrue(liked.getJSONObject("interactionState").getBoolean("liked"));
        data(request(
                "PATCH",
                "/v1/users/me",
                "req-persist-user",
                "{\"nickname\":\"持久化用户\",\"bio\":\"重启后仍应保留\"}"
        ));
        data(request(
                "POST",
                "/v1/comments",
                "req-persist-comment",
                "{\"commentId\":\"comment_persist_001\",\"targetType\":\"ad\",\"targetId\":\"ad_001\",\"content\":\"persisted\"}"
        ));
        data(request(
                "POST",
                "/v1/notifications/read",
                "req-persist-read",
                "{\"notificationIds\":[\"nt_like_001\"]}"
        ));
        data(request(
                "POST",
                "/v1/ai/search",
                "req-persist-ai",
                "{\"sessionId\":\"search_session_persist_001\",\"query\":\"持久化搜索\"}"
        ));
        data(request(
                "POST",
                "/v1/events/batch",
                "req-persist-events",
                "{\"events\":[{\"eventType\":\"DETAIL_OPEN\",\"adId\":\"ad_001\"}]}"
        ));
        server.stop();

        server = BackendServer.create(0, stateDirectory);
        server.start();

        JSONObject restoredAd = adData(request("GET", "/v1/ads/ad_001", "req-restored-ad"));
        assertTrue(restoredAd.getJSONObject("interactionState").getBoolean("liked"));
        assertEquals(3, restoredAd.getJSONObject("stats").getInt("likeCount"));

        JSONObject restoredMe = data(request("GET", "/v1/users/me", "req-restored-user"))
                .getJSONObject("userProfile");
        assertEquals("持久化用户", restoredMe.getString("nickname"));

        JSONObject restoredComments = data(request(
                "GET",
                "/v1/comments?targetType=ad&targetId=ad_001",
                "req-restored-comment"
        ));
        assertTrue(restoredComments.toString().contains("comment_persist_001"));

        JSONObject restoredSummary = data(request("GET", "/v1/notifications/summary", "req-restored-summary"))
                .getJSONObject("notificationSummary");
        assertEquals(3, restoredSummary.getInt("totalUnreadCount"));

        JSONObject restoredSession = data(request(
                "GET",
                "/v1/ai/search/sessions/search_session_persist_001",
                "req-restored-ai"
        ));
        assertEquals("search_session_persist_001", restoredSession.getJSONObject("session").getString("sessionId"));

        JSONObject restoredAnalytics = data(request("GET", "/v1/analytics/summary", "req-restored-analytics"));
        assertEquals(1, restoredAnalytics.getInt("totalEventCount"));
    }

    private HttpResult request(String method, String path, String requestId) throws Exception {
        return request(method, path, requestId, "");
    }

    private HttpResult request(String method, String path, String requestId, String requestBody) throws Exception {
        return requestAsUser(method, path, requestId, "", requestBody);
    }

    private HttpResult requestAsUser(String method, String path, String requestId, String userId) throws Exception {
        return requestAsUser(method, path, requestId, userId, "");
    }

    private HttpResult requestAsUser(
            String method,
            String path,
            String requestId,
            String userId,
            String requestBody
    ) throws Exception {
        URI uri = server.baseUri().resolve(path);
        HttpRequest.BodyPublisher bodyPublisher = requestBody == null || requestBody.isEmpty()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .method(method, bodyPublisher)
                .header("X-Request-Id", requestId)
                .header("Content-Type", "application/json; charset=utf-8");
        if (userId != null && !userId.isBlank()) {
            requestBuilder.header("X-NBN-User-Id", userId);
        }
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        return new HttpResult(
                response.statusCode(),
                response.headers().firstValue("X-Request-Id").orElse(""),
                response.body()
        );
    }

    private HttpResult uncheckedRequestAsUser(String method, String path, String requestId, String userId) {
        try {
            return requestAsUser(method, path, requestId, userId);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static JSONObject data(HttpResult result) {
        return new JSONObject(result.body).getJSONObject("data");
    }

    private static JSONObject adData(HttpResult result) {
        return data(result).getJSONObject("ad");
    }

    private static void assertNoDuplicates(JSONArray values) {
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < values.length(); index++) {
            assertTrue(seen.add(values.optString(index)));
        }
    }

    private static String readBody(InputStream inputStream) throws IOException {
        try (InputStream source = inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            source.transferTo(output);
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private record HttpResult(int statusCode, String requestId, String body) {
    }
}
