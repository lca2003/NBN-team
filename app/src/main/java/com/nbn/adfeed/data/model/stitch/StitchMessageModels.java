package com.nbn.adfeed.data.model.stitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StitchMessageModels {
    private StitchMessageModels() {
    }

    public static final class NotificationSummary {
        public final int likeUnreadCount;
        public final int collectUnreadCount;
        public final int followerUnreadCount;
        public final int commentUnreadCount;

        public NotificationSummary(int likeUnreadCount, int collectUnreadCount,
                                   int followerUnreadCount, int commentUnreadCount) {
            this.likeUnreadCount = Math.max(0, likeUnreadCount);
            this.collectUnreadCount = Math.max(0, collectUnreadCount);
            this.followerUnreadCount = Math.max(0, followerUnreadCount);
            this.commentUnreadCount = Math.max(0, commentUnreadCount);
        }
    }

    public static final class NotificationItem {
        public final String notificationId;
        public final String type;
        public final String sourceUserId;
        public final String targetAdId;
        public final String content;
        public final String timeText;
        public final boolean read;

        public NotificationItem(String notificationId, String type, String sourceUserId,
                                String targetAdId, String content, String timeText, boolean read) {
            this.notificationId = safe(notificationId);
            this.type = safe(type);
            this.sourceUserId = safe(sourceUserId);
            this.targetAdId = safe(targetAdId);
            this.content = safe(content);
            this.timeText = safe(timeText);
            this.read = read;
        }
    }

    public static final class Conversation {
        public final String conversationId;
        public final String avatarUrl;
        public final String title;
        public final String lastMessage;
        public final String timeText;
        public final int unreadCount;
        public final boolean groupChat;

        public Conversation(String conversationId, String avatarUrl, String title,
                            String lastMessage, String timeText, int unreadCount, boolean groupChat) {
            this.conversationId = safe(conversationId);
            this.avatarUrl = safe(avatarUrl);
            this.title = safe(title);
            this.lastMessage = safe(lastMessage);
            this.timeText = safe(timeText);
            this.unreadCount = Math.max(0, unreadCount);
            this.groupChat = groupChat;
        }
    }

    public static final class Message {
        public final String messageId;
        public final String conversationId;
        public final String senderUserId;
        public final String content;
        public final String timeText;
        public final String messageType;

        public Message(String messageId, String conversationId, String senderUserId,
                       String content, String timeText, String messageType) {
            this.messageId = safe(messageId);
            this.conversationId = safe(conversationId);
            this.senderUserId = safe(senderUserId);
            this.content = safe(content);
            this.timeText = safe(timeText);
            this.messageType = safe(messageType);
        }
    }

    public static final class AiAssistantDigest {
        public final String digestId;
        public final String title;
        public final String summary;
        public final List<String> recommendationAdIds;
        public final String generatedAtText;

        public AiAssistantDigest(String digestId, String title, String summary,
                                 List<String> recommendationAdIds, String generatedAtText) {
            this.digestId = safe(digestId);
            this.title = safe(title);
            this.summary = safe(summary);
            this.recommendationAdIds = immutable(recommendationAdIds);
            this.generatedAtText = safe(generatedAtText);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null ? Collections.emptyList() : values));
    }
}
