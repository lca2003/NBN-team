package com.nbn.adfeed.data.model.stitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StitchFeedModels {
    private StitchFeedModels() {
    }

    public static final class MediaAsset {
        public final String url;
        public final String localAssetName;
        public final int width;
        public final int height;
        public final String cropStrategy;
        public final String dominantColor;
        public final String blurHash;

        public MediaAsset(String url, String localAssetName, int width, int height,
                          String cropStrategy, String dominantColor, String blurHash) {
            this.url = safe(url);
            this.localAssetName = safe(localAssetName);
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
            this.cropStrategy = safe(cropStrategy);
            this.dominantColor = safe(dominantColor);
            this.blurHash = safe(blurHash);
        }
    }

    public static final class CreatorProfile {
        public final String userId;
        public final String nickname;
        public final String avatarUrl;
        public final boolean verified;
        public final String bio;

        public CreatorProfile(String userId, String nickname, String avatarUrl, boolean verified, String bio) {
            this.userId = safe(userId);
            this.nickname = safe(nickname);
            this.avatarUrl = safe(avatarUrl);
            this.verified = verified;
            this.bio = safe(bio);
        }
    }

    public static final class AdTag {
        public final String id;
        public final String name;
        public final String category;

        public AdTag(String id, String name, String category) {
            this.id = safe(id);
            this.name = safe(name);
            this.category = safe(category);
        }
    }

    public static final class StitchAdStats {
        public final int likeCount;
        public final int commentCount;
        public final int collectCount;
        public final int shareCount;
        public final int exposureCount;
        public final int clickCount;

        public StitchAdStats(int likeCount, int commentCount, int collectCount,
                             int shareCount, int exposureCount, int clickCount) {
            this.likeCount = Math.max(0, likeCount);
            this.commentCount = Math.max(0, commentCount);
            this.collectCount = Math.max(0, collectCount);
            this.shareCount = Math.max(0, shareCount);
            this.exposureCount = Math.max(0, exposureCount);
            this.clickCount = Math.max(0, clickCount);
        }
    }

    public static final class StitchInteractionState {
        public final boolean liked;
        public final boolean collected;
        public final boolean followingCreator;
        public final boolean shared;

        public StitchInteractionState(boolean liked, boolean collected, boolean followingCreator, boolean shared) {
            this.liked = liked;
            this.collected = collected;
            this.followingCreator = followingCreator;
            this.shared = shared;
        }
    }

    public static final class FeedChannel {
        public final String id;
        public final String title;
        public final String subtitle;
        public final boolean enabled;
        public final int sortIndex;

        public FeedChannel(String id, String title, String subtitle, boolean enabled, int sortIndex) {
            this.id = safe(id);
            this.title = safe(title);
            this.subtitle = safe(subtitle);
            this.enabled = enabled;
            this.sortIndex = Math.max(0, sortIndex);
        }
    }

    public static final class FeedAdItem {
        public final String adId;
        public final String title;
        public final String subtitle;
        public final String description;
        public final MediaAsset cover;
        public final MediaAsset video;
        public final String adType;
        public final String brand;
        public final String category;
        public final String publishTime;
        public final CreatorProfile creator;
        public final List<AdTag> tags;
        public final StitchAdStats stats;
        public final StitchInteractionState interactionState;

        public FeedAdItem(String adId, String title, String subtitle, String description,
                          MediaAsset cover, MediaAsset video, String adType, String brand,
                          String category, String publishTime, CreatorProfile creator,
                          List<AdTag> tags, StitchAdStats stats,
                          StitchInteractionState interactionState) {
            this.adId = safe(adId);
            this.title = safe(title);
            this.subtitle = safe(subtitle);
            this.description = safe(description);
            this.cover = cover;
            this.video = video;
            this.adType = safe(adType);
            this.brand = safe(brand);
            this.category = safe(category);
            this.publishTime = safe(publishTime);
            this.creator = creator;
            this.tags = immutable(tags);
            this.stats = stats;
            this.interactionState = interactionState;
        }
    }

    public static final class FeedPage {
        public final String cursor;
        public final String nextCursor;
        public final boolean hasMore;
        public final int totalCount;
        public final List<FeedAdItem> items;

        public FeedPage(String cursor, String nextCursor, boolean hasMore, int totalCount, List<FeedAdItem> items) {
            this.cursor = safe(cursor);
            this.nextCursor = safe(nextCursor);
            this.hasMore = hasMore;
            this.totalCount = Math.max(0, totalCount);
            this.items = immutable(items);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null ? Collections.emptyList() : values));
    }
}
