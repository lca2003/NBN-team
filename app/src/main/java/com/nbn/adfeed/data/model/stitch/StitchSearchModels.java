package com.nbn.adfeed.data.model.stitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StitchSearchModels {
    private StitchSearchModels() {
    }

    public static final class AiSearchSession {
        public final String sessionId;
        public final String userQuery;
        public final long createdAtMillis;
        public final String context;

        public AiSearchSession(String sessionId, String userQuery, long createdAtMillis, String context) {
            this.sessionId = safe(sessionId);
            this.userQuery = safe(userQuery);
            this.createdAtMillis = Math.max(0L, createdAtMillis);
            this.context = safe(context);
        }
    }

    public static final class AiSearchMessage {
        public final String messageId;
        public final String sessionId;
        public final String senderType;
        public final String content;
        public final String messageType;
        public final long createdAtMillis;

        public AiSearchMessage(String messageId, String sessionId, String senderType,
                               String content, String messageType, long createdAtMillis) {
            this.messageId = safe(messageId);
            this.sessionId = safe(sessionId);
            this.senderType = safe(senderType);
            this.content = safe(content);
            this.messageType = safe(messageType);
            this.createdAtMillis = Math.max(0L, createdAtMillis);
        }
    }

    public static final class AiSearchResult {
        public final String adId;
        public final String title;
        public final String reason;
        public final String priceText;
        public final String imageUrl;
        public final String ctaText;
        public final AiRecommendationReason recommendationReason;

        public AiSearchResult(String adId, String title, String reason, String priceText,
                              String imageUrl, String ctaText,
                              AiRecommendationReason recommendationReason) {
            this.adId = safe(adId);
            this.title = safe(title);
            this.reason = safe(reason);
            this.priceText = safe(priceText);
            this.imageUrl = safe(imageUrl);
            this.ctaText = safe(ctaText);
            this.recommendationReason = recommendationReason;
        }
    }

    public static final class AiRecommendationReason {
        public final String summary;
        public final List<String> matchedTags;
        public final String valueExplanation;
        public final double score;

        public AiRecommendationReason(String summary, List<String> matchedTags,
                                      String valueExplanation, double score) {
            this.summary = safe(summary);
            this.matchedTags = immutable(matchedTags);
            this.valueExplanation = safe(valueExplanation);
            this.score = Math.max(0.0d, score);
        }
    }

    public static final class SearchSuggestion {
        public final String suggestionId;
        public final String text;
        public final String icon;
        public final int sortIndex;

        public SearchSuggestion(String suggestionId, String text, String icon, int sortIndex) {
            this.suggestionId = safe(suggestionId);
            this.text = safe(text);
            this.icon = safe(icon);
            this.sortIndex = Math.max(0, sortIndex);
        }
    }

    public static final class AiFallback {
        public final boolean aiAvailable;
        public final String message;
        public final List<String> defaultAdIds;

        public AiFallback(boolean aiAvailable, String message, List<String> defaultAdIds) {
            this.aiAvailable = aiAvailable;
            this.message = safe(message);
            this.defaultAdIds = immutable(defaultAdIds);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null ? Collections.emptyList() : values));
    }
}
