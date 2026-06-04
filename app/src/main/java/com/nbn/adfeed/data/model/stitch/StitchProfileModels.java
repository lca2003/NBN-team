package com.nbn.adfeed.data.model.stitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StitchProfileModels {
    private StitchProfileModels() {
    }

    public static final class UserProfile {
        public final String userId;
        public final String nickname;
        public final String avatarUrl;
        public final String level;
        public final String bio;
        public final ProfileStats stats;
        public final List<Achievement> achievements;

        public UserProfile(String userId, String nickname, String avatarUrl,
                           String level, String bio, ProfileStats stats,
                           List<Achievement> achievements) {
            this.userId = safe(userId);
            this.nickname = safe(nickname);
            this.avatarUrl = safe(avatarUrl);
            this.level = safe(level);
            this.bio = safe(bio);
            this.stats = stats;
            this.achievements = immutable(achievements);
        }
    }

    public static final class ProfileStats {
        public final int likedAndCollectedCount;
        public final int followingCount;
        public final int followerCount;
        public final int postCount;

        public ProfileStats(int likedAndCollectedCount, int followingCount, int followerCount, int postCount) {
            this.likedAndCollectedCount = Math.max(0, likedAndCollectedCount);
            this.followingCount = Math.max(0, followingCount);
            this.followerCount = Math.max(0, followerCount);
            this.postCount = Math.max(0, postCount);
        }
    }

    public static final class Achievement {
        public final String achievementId;
        public final String title;
        public final String description;
        public final String icon;
        public final boolean unlocked;

        public Achievement(String achievementId, String title, String description, String icon, boolean unlocked) {
            this.achievementId = safe(achievementId);
            this.title = safe(title);
            this.description = safe(description);
            this.icon = safe(icon);
            this.unlocked = unlocked;
        }
    }

    public static final class ProfilePost {
        public final String postId;
        public final String tab;
        public final String title;
        public final String coverUrl;
        public final String sourceAdId;
        public final int likeCount;
        public final String timeText;

        public ProfilePost(String postId, String tab, String title, String coverUrl,
                           String sourceAdId, int likeCount, String timeText) {
            this.postId = safe(postId);
            this.tab = safe(tab);
            this.title = safe(title);
            this.coverUrl = safe(coverUrl);
            this.sourceAdId = safe(sourceAdId);
            this.likeCount = Math.max(0, likeCount);
            this.timeText = safe(timeText);
        }
    }

    public static final class FollowRelation {
        public final String relationId;
        public final String userId;
        public final String targetUserId;
        public final String relationType;
        public final boolean following;

        public FollowRelation(String relationId, String userId, String targetUserId,
                              String relationType, boolean following) {
            this.relationId = safe(relationId);
            this.userId = safe(userId);
            this.targetUserId = safe(targetUserId);
            this.relationType = safe(relationType);
            this.following = following;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null ? Collections.emptyList() : values));
    }
}
