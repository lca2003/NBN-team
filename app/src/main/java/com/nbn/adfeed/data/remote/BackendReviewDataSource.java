package com.nbn.adfeed.data.remote;

import com.nbn.adfeed.data.model.stitch.StitchDetailModels;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BackendReviewDataSource {
    public interface Transport {
        String get(String path) throws RemoteAdException;

        String post(String path, String body) throws RemoteAdException;
    }

    public static final class ReviewPage {
        public final String adId;
        public final String cursor;
        public final String nextCursor;
        public final boolean hasMore;
        public final int totalCount;
        public final List<StitchDetailModels.Review> items;

        ReviewPage(JSONObject data) {
            this.adId = data.optString("adId");
            this.cursor = data.optString("cursor");
            this.nextCursor = data.optString("nextCursor");
            this.hasMore = data.optBoolean("hasMore", false);
            this.totalCount = Math.max(0, data.optInt("totalCount", 0));
            this.items = parseReviews(data.optJSONArray("items"));
        }
    }

    public static final class CommentPage {
        public final String targetType;
        public final String targetId;
        public final int totalCount;
        public final List<StitchDetailModels.Comment> items;

        CommentPage(JSONObject data) {
            this.targetType = data.optString("targetType");
            this.targetId = data.optString("targetId");
            this.totalCount = Math.max(0, data.optInt("totalCount", 0));
            this.items = parseComments(data.optJSONArray("items"));
        }
    }

    private final Transport transport;

    public BackendReviewDataSource(Transport transport) {
        this.transport = transport == null ? defaultTransport() : transport;
    }

    public static BackendReviewDataSource defaultDataSource() {
        return new BackendReviewDataSource(defaultTransport());
    }

    public ReviewPage reviews(String adId, String cursor, int limit) throws RemoteAdException {
        JSONObject data = BackendJson.dataFromEnvelope(
                transport.get(pagePath("/v1/ads/" + BackendJson.encode(adId) + "/reviews", cursor, limit)),
                "review"
        );
        return new ReviewPage(data);
    }

    public StitchDetailModels.Review createReview(
            String adId,
            String reviewId,
            String nickname,
            String content
    ) throws RemoteAdException {
        JSONObject body = BackendJson.object(
                "reviewId", BackendJson.safe(reviewId),
                "nickname", BackendJson.safe(nickname),
                "content", BackendJson.safe(content)
        );
        JSONObject data = BackendJson.dataFromEnvelope(
                transport.post("/v1/ads/" + BackendJson.encode(adId) + "/reviews", body.toString()),
                "review"
        );
        return parseReview(BackendJson.requiredObject(data, "review", "review"));
    }

    public StitchDetailModels.Review likeReview(String reviewId) throws RemoteAdException {
        JSONObject data = BackendJson.dataFromEnvelope(
                transport.post("/v1/reviews/" + BackendJson.encode(reviewId) + "/like", ""),
                "review"
        );
        return parseReview(BackendJson.requiredObject(data, "review", "review"));
    }

    public CommentPage comments(String targetType, String targetId) throws RemoteAdException {
        String path = "/v1/comments?targetType=" + BackendJson.encode(targetType)
                + "&targetId=" + BackendJson.encode(targetId);
        JSONObject data = BackendJson.dataFromEnvelope(transport.get(path), "review");
        return new CommentPage(data);
    }

    public StitchDetailModels.Comment createComment(
            String commentId,
            String targetType,
            String targetId,
            String parentCommentId,
            String content
    ) throws RemoteAdException {
        JSONObject body = BackendJson.object(
                "commentId", BackendJson.safe(commentId),
                "targetType", BackendJson.safe(targetType),
                "targetId", BackendJson.safe(targetId),
                "parentCommentId", BackendJson.safe(parentCommentId),
                "content", BackendJson.safe(content)
        );
        JSONObject data = BackendJson.dataFromEnvelope(transport.post("/v1/comments", body.toString()), "review");
        return parseComment(BackendJson.requiredObject(data, "comment", "review"));
    }

    private static List<StitchDetailModels.Review> parseReviews(JSONArray reviewsJson) {
        if (reviewsJson == null) {
            return Collections.emptyList();
        }
        List<StitchDetailModels.Review> reviews = new ArrayList<>();
        for (int index = 0; index < reviewsJson.length(); index++) {
            JSONObject review = reviewsJson.optJSONObject(index);
            if (review != null) {
                reviews.add(parseReview(review));
            }
        }
        return reviews;
    }

    private static StitchDetailModels.Review parseReview(JSONObject review) {
        return new StitchDetailModels.Review(
                review.optString("reviewId"),
                review.optString("userAvatarUrl"),
                review.optString("nickname"),
                review.optString("timeText"),
                review.optString("content"),
                review.optInt("likeCount", 0)
        );
    }

    private static List<StitchDetailModels.Comment> parseComments(JSONArray commentsJson) {
        if (commentsJson == null) {
            return Collections.emptyList();
        }
        List<StitchDetailModels.Comment> comments = new ArrayList<>();
        for (int index = 0; index < commentsJson.length(); index++) {
            JSONObject comment = commentsJson.optJSONObject(index);
            if (comment != null) {
                comments.add(parseComment(comment));
            }
        }
        return comments;
    }

    private static StitchDetailModels.Comment parseComment(JSONObject comment) {
        return new StitchDetailModels.Comment(
                comment.optString("commentId"),
                comment.optString("targetType"),
                comment.optString("targetId"),
                comment.optString("parentCommentId"),
                comment.optString("content"),
                comment.optString("timeText")
        );
    }

    private static String pagePath(String basePath, String cursor, int limit) {
        StringBuilder path = new StringBuilder(basePath)
                .append("?cursor=")
                .append(BackendJson.encode(cursor));
        if (limit > 0) {
            path.append("&limit=").append(limit);
        }
        return path.toString();
    }

    private static Transport defaultTransport() {
        return new Transport() {
            @Override
            public String get(String path) throws RemoteAdException {
                return request("GET", path, "");
            }

            @Override
            public String post(String path, String body) throws RemoteAdException {
                return request("POST", path, body);
            }

            private String request(String method, String path, String body) throws RemoteAdException {
                RemoteAdException lastException = null;
                for (BackendConfig candidate : BackendConfig.defaultCandidates()) {
                    try {
                        HttpApiClient client = new HttpApiClient(candidate);
                        return "POST".equals(method) ? client.post(path, body) : client.get(path);
                    } catch (RemoteAdException exception) {
                        lastException = exception;
                    }
                }
                throw lastException == null
                        ? new RemoteAdException(RemoteAdException.Reason.NETWORK, "Backend review API unavailable")
                        : lastException;
            }
        };
    }
}
