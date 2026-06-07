package com.nbn.adfeed.data.remote;

import com.nbn.adfeed.data.model.stitch.StitchDetailModels;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class BackendReviewDataSourceTest {
    @Test
    public void reviewClientCallsReviewAndCommentRoutes() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        BackendReviewDataSource dataSource = new BackendReviewDataSource(transport);

        BackendReviewDataSource.ReviewPage reviews = dataSource.reviews("ad_001", "", 10);
        StitchDetailModels.Review createdReview = dataSource.createReview(
                "ad_001",
                "review_client_001",
                "客户端用户",
                "很好用"
        );
        StitchDetailModels.Review likedReview = dataSource.likeReview("review_client_001");
        BackendReviewDataSource.CommentPage comments = dataSource.comments("ad", "ad_001");
        StitchDetailModels.Comment createdComment = dataSource.createComment(
                "comment_client_001",
                "ad",
                "ad_001",
                "",
                "请问还有货吗"
        );

        assertEquals("ad_001", reviews.adId);
        assertEquals(1, reviews.items.size());
        assertEquals("review_client_001", createdReview.reviewId);
        assertEquals(8, likedReview.likeCount);
        assertEquals(1, comments.items.size());
        assertEquals("comment_client_001", createdComment.commentId);
        assertEquals("GET /v1/ads/ad_001/reviews?cursor=&limit=10", transport.calls.get(0));
        assertEquals("POST /v1/ads/ad_001/reviews", transport.calls.get(1));
        assertEquals("POST /v1/reviews/review_client_001/like", transport.calls.get(2));
        assertEquals("GET /v1/comments?targetType=ad&targetId=ad_001", transport.calls.get(3));
        assertEquals("POST /v1/comments", transport.calls.get(4));
        assertTrue(transport.requestBodies.get(1).contains("客户端用户"));
        assertTrue(transport.requestBodies.get(4).contains("请问还有货吗"));
    }

    @Test(expected = RemoteAdException.class)
    public void nonOkEnvelopeThrowsRemoteException() throws Exception {
        BackendReviewDataSource dataSource = new BackendReviewDataSource(new RecordingTransport() {
            @Override
            public String get(String path) {
                return "{\"requestId\":\"req-test\",\"code\":\"REMOTE_ERROR\",\"message\":\"down\",\"data\":null}";
            }
        });

        dataSource.reviews("ad_missing", "", 10);
    }

    private static class RecordingTransport implements BackendReviewDataSource.Transport {
        private final java.util.List<String> calls = new java.util.ArrayList<>();
        private final java.util.List<String> requestBodies = new java.util.ArrayList<>();

        @Override
        public String get(String path) {
            calls.add("GET " + path);
            requestBodies.add("");
            if (path.startsWith("/v1/comments")) {
                return ok("{\"targetType\":\"ad\",\"targetId\":\"ad_001\",\"totalCount\":1,"
                        + "\"items\":[{\"commentId\":\"comment_001\",\"targetType\":\"ad\","
                        + "\"targetId\":\"ad_001\",\"parentCommentId\":\"\",\"content\":\"有货吗\","
                        + "\"timeText\":\"刚刚\"}]}");
            }
            return ok("{\"adId\":\"ad_001\",\"cursor\":\"\",\"nextCursor\":\"\",\"hasMore\":false,"
                    + "\"totalCount\":1,\"items\":[" + reviewJson("review_001", 7) + "]}");
        }

        @Override
        public String post(String path, String body) {
            calls.add("POST " + path);
            requestBodies.add(body == null ? "" : body);
            if (path.endsWith("/like")) {
                return ok("{\"review\":" + reviewJson("review_client_001", 8) + "}");
            }
            if (path.startsWith("/v1/comments")) {
                return ok("{\"comment\":{\"commentId\":\"comment_client_001\",\"targetType\":\"ad\","
                        + "\"targetId\":\"ad_001\",\"parentCommentId\":\"\",\"content\":\"请问还有货吗\","
                        + "\"timeText\":\"刚刚\"}}");
            }
            return ok("{\"review\":" + reviewJson("review_client_001", 0) + "}");
        }

        private static String ok(String dataJson) {
            return "{\"requestId\":\"req-test\",\"code\":\"OK\",\"message\":\"ok\",\"data\":" + dataJson + "}";
        }

        private static String reviewJson(String reviewId, int likeCount) {
            return "{\"reviewId\":\"" + reviewId + "\",\"userAvatarUrl\":\"avatar\","
                    + "\"nickname\":\"客户端用户\",\"timeText\":\"刚刚\",\"content\":\"很好用\","
                    + "\"likeCount\":" + likeCount + "}";
        }
    }
}
