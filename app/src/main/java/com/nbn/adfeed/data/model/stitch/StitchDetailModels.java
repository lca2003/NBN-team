package com.nbn.adfeed.data.model.stitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StitchDetailModels {
    private StitchDetailModels() {
    }

    public static final class AdDetail {
        public final String adId;
        public final String title;
        public final String longCopy;
        public final String aiDeepInsight;
        public final List<String> sellingPoints;
        public final List<StitchFeedModels.MediaAsset> mediaAssets;
        public final Merchant merchant;
        public final Offer offer;
        public final List<Review> reviews;
        public final List<Comment> comments;
        public final List<RelatedItem> relatedItems;

        public AdDetail(String adId, String title, String longCopy, String aiDeepInsight,
                        List<String> sellingPoints, List<StitchFeedModels.MediaAsset> mediaAssets,
                        Merchant merchant, Offer offer, List<Review> reviews,
                        List<Comment> comments, List<RelatedItem> relatedItems) {
            this.adId = safe(adId);
            this.title = safe(title);
            this.longCopy = safe(longCopy);
            this.aiDeepInsight = safe(aiDeepInsight);
            this.sellingPoints = immutable(sellingPoints);
            this.mediaAssets = immutable(mediaAssets);
            this.merchant = merchant;
            this.offer = offer;
            this.reviews = immutable(reviews);
            this.comments = immutable(comments);
            this.relatedItems = immutable(relatedItems);
        }
    }

    public static final class Merchant {
        public final String merchantId;
        public final String name;
        public final String avatarUrl;
        public final double rating;
        public final String address;
        public final String distance;
        public final String businessStatus;

        public Merchant(String merchantId, String name, String avatarUrl, double rating,
                        String address, String distance, String businessStatus) {
            this.merchantId = safe(merchantId);
            this.name = safe(name);
            this.avatarUrl = safe(avatarUrl);
            this.rating = Math.max(0.0d, rating);
            this.address = safe(address);
            this.distance = safe(distance);
            this.businessStatus = safe(businessStatus);
        }
    }

    public static final class Offer {
        public final String offerId;
        public final String priceText;
        public final String originalPriceText;
        public final String discountText;
        public final String ctaText;

        public Offer(String offerId, String priceText, String originalPriceText, String discountText, String ctaText) {
            this.offerId = safe(offerId);
            this.priceText = safe(priceText);
            this.originalPriceText = safe(originalPriceText);
            this.discountText = safe(discountText);
            this.ctaText = safe(ctaText);
        }
    }

    public static final class Review {
        public final String reviewId;
        public final String userAvatarUrl;
        public final String nickname;
        public final String timeText;
        public final String content;
        public final int likeCount;

        public Review(String reviewId, String userAvatarUrl, String nickname, String timeText,
                      String content, int likeCount) {
            this.reviewId = safe(reviewId);
            this.userAvatarUrl = safe(userAvatarUrl);
            this.nickname = safe(nickname);
            this.timeText = safe(timeText);
            this.content = safe(content);
            this.likeCount = Math.max(0, likeCount);
        }
    }

    public static final class Comment {
        public final String commentId;
        public final String targetType;
        public final String targetId;
        public final String parentCommentId;
        public final String content;
        public final String timeText;

        public Comment(String commentId, String targetType, String targetId,
                       String parentCommentId, String content, String timeText) {
            this.commentId = safe(commentId);
            this.targetType = safe(targetType);
            this.targetId = safe(targetId);
            this.parentCommentId = safe(parentCommentId);
            this.content = safe(content);
            this.timeText = safe(timeText);
        }
    }

    public static final class RelatedItem {
        public final String itemId;
        public final String title;
        public final String imageUrl;
        public final String reason;
        public final String ctaText;

        public RelatedItem(String itemId, String title, String imageUrl, String reason, String ctaText) {
            this.itemId = safe(itemId);
            this.title = safe(title);
            this.imageUrl = safe(imageUrl);
            this.reason = safe(reason);
            this.ctaText = safe(ctaText);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null ? Collections.emptyList() : values));
    }
}
