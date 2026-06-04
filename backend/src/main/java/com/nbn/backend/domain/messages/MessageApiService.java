package com.nbn.backend.domain.messages;

import com.nbn.backend.domain.users.UserApiService;
import com.nbn.backend.domain.users.UserSession;
import com.nbn.backend.store.JsonSeedStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class MessageApiService {
    private final JsonSeedStore seedStore;
    private final UserSession session;
    private final JSONObject notificationSummarySeed;
    private final JSONArray notifications;
    private final JSONArray conversations;
    private final JSONArray messages;
    private final JSONObject aiAssistantDigest;

    public MessageApiService(JsonSeedStore seedStore, UserSession session) {
        this.seedStore = seedStore;
        this.session = session;
        JSONObject seed = seedStore.documentCopy("messages.json");
        this.notificationSummarySeed = seed.getJSONObject("notificationSummary");
        this.notifications = seed.getJSONArray("notifications");
        this.conversations = seed.getJSONArray("conversations");
        this.messages = seed.getJSONArray("messages");
        this.aiAssistantDigest = seed.getJSONObject("aiAssistantDigest");
    }

    public synchronized String notificationSummaryJson() {
        return "{\"notificationSummary\":" + computedNotificationSummary() + "}";
    }

    public synchronized String notificationsJson(URI requestUri) {
        Query query = Query.from(requestUri);
        String type = query.value("type");
        JSONArray filtered = new JSONArray();
        for (int index = 0; index < notifications.length(); index++) {
            JSONObject notification = notifications.getJSONObject(index);
            if (type.isBlank() || type.equals(notification.optString("type"))) {
                filtered.put(copy(notification));
            }
        }
        return page("notifications", filtered, query.cursor(), query.limit(filtered.length()))
                .put("type", type)
                .toString();
    }

    public synchronized String markRead(String requestBody) {
        requireKnownCurrentUser();
        JSONObject body = objectOrEmpty(requestBody);
        JSONArray ids = body.optJSONArray("notificationIds");
        String type = body.optString("type", "");
        int readCount = 0;
        for (int index = 0; index < notifications.length(); index++) {
            JSONObject notification = notifications.getJSONObject(index);
            boolean matchId = ids != null && contains(ids, notification.optString("notificationId"));
            boolean matchType = !type.isBlank() && type.equals(notification.optString("type"));
            if (matchId || matchType) {
                if (!notification.optBoolean("read", false)) {
                    readCount++;
                }
                notification.put("read", true);
            }
        }
        persistMessages();
        return new JSONObject()
                .put("readCount", readCount)
                .put("notificationSummary", computedNotificationSummary())
                .toString();
    }

    public synchronized String markAllRead() {
        requireKnownCurrentUser();
        int readCount = 0;
        for (int index = 0; index < notifications.length(); index++) {
            JSONObject notification = notifications.getJSONObject(index);
            if (!notification.optBoolean("read", false)) {
                readCount++;
            }
            notification.put("read", true);
        }
        persistMessages();
        return new JSONObject()
                .put("readCount", readCount)
                .put("notificationSummary", computedNotificationSummary())
                .toString();
    }

    public synchronized String conversationsJson(URI requestUri) {
        Query query = Query.from(requestUri);
        JSONArray items = new JSONArray();
        for (int index = 0; index < conversations.length(); index++) {
            items.put(copy(conversations.getJSONObject(index)));
        }
        return page("conversations", items, query.cursor(), query.limit(items.length())).toString();
    }

    public synchronized String messagesJson(String conversationId, URI requestUri) {
        if (conversation(conversationId) == null) {
            return null;
        }
        Query query = Query.from(requestUri);
        JSONArray filtered = new JSONArray();
        for (int index = 0; index < messages.length(); index++) {
            JSONObject message = messages.getJSONObject(index);
            if (conversationId.equals(message.optString("conversationId"))) {
                filtered.put(copy(message));
            }
        }
        return page("messages", filtered, query.cursor(), query.limit(filtered.length()))
                .put("conversationId", conversationId)
                .toString();
    }

    public synchronized String appendMessage(String conversationId, String requestBody) {
        String currentUserId = requireKnownCurrentUser();
        JSONObject body = objectOrEmpty(requestBody);
        String targetUserId = body.optString("targetUserId", "");
        JSONObject conversation = targetUserId.isBlank()
                ? conversation(conversationId)
                : directConversationFor(currentUserId, targetUserId);
        if (conversation == null) {
            conversation = conversation(conversationId);
        }
        if (conversation == null) {
            conversation = createDirectConversation(conversationId, targetUserId);
        }
        String effectiveConversationId = conversation.getString("conversationId");
        JSONObject message = new JSONObject();
        message.put("messageId", body.optString("messageId", "dm_" + UUID.randomUUID()));
        message.put("conversationId", effectiveConversationId);
        message.put("senderUserId", currentUserId);
        message.put("content", body.optString("content", ""));
        message.put("timeText", body.optString("timeText", "刚刚"));
        message.put("messageType", body.optString("messageType", "text"));
        messages.put(message);

        conversation.put("lastMessage", message.optString("content"));
        conversation.put("timeText", message.optString("timeText"));
        if (!currentUserId.equals(message.optString("senderUserId"))) {
            conversation.put("unreadCount", conversation.optInt("unreadCount", 0) + 1);
        }
        persistMessages();
        return new JSONObject()
                .put("message", copy(message))
                .put("conversation", copy(conversation))
                .toString();
    }

    public synchronized String aiAssistantDigestJson() {
        return "{\"aiAssistantDigest\":" + copy(aiAssistantDigest) + "}";
    }

    private void persistMessages() {
        seedStore.writeState("messages.json", new JSONObject()
                .put("notificationSummary", computedNotificationSummary())
                .put("notifications", new JSONArray(notifications.toString()))
                .put("conversations", new JSONArray(conversations.toString()))
                .put("messages", new JSONArray(messages.toString()))
                .put("aiAssistantDigest", copy(aiAssistantDigest)));
    }

    private JSONObject computedNotificationSummary() {
        int like = 0;
        int collect = 0;
        int follower = 0;
        int comment = 0;
        for (int index = 0; index < notifications.length(); index++) {
            JSONObject notification = notifications.getJSONObject(index);
            if (notification.optBoolean("read", false)) {
                continue;
            }
            switch (notification.optString("type")) {
                case "like" -> like++;
                case "collect" -> collect++;
                case "follower" -> follower++;
                case "comment" -> comment++;
                default -> {
                }
            }
        }
        return copy(notificationSummarySeed)
                .put("likeUnreadCount", like)
                .put("collectUnreadCount", collect)
                .put("followerUnreadCount", follower)
                .put("commentUnreadCount", comment)
                .put("totalUnreadCount", like + collect + follower + comment);
    }

    private JSONObject conversation(String conversationId) {
        for (int index = 0; index < conversations.length(); index++) {
            JSONObject conversation = conversations.getJSONObject(index);
            if (conversationId.equals(conversation.optString("conversationId"))) {
                return conversation;
            }
        }
        return null;
    }

    private JSONObject directConversationFor(String currentUserId, String targetUserId) {
        String normalizedTarget = targetUserId == null ? "" : targetUserId.trim();
        if (currentUserId == null || currentUserId.isBlank() || normalizedTarget.isBlank()) {
            return null;
        }
        for (int index = 0; index < conversations.length(); index++) {
            JSONObject conversation = conversations.getJSONObject(index);
            JSONArray participants = conversation.optJSONArray("participantUserIds");
            boolean sameTarget = normalizedTarget.equals(conversation.optString("targetUserId"));
            boolean hasParticipants = participants != null
                    && contains(participants, currentUserId)
                    && contains(participants, normalizedTarget);
            if (sameTarget && hasParticipants) {
                return conversation;
            }
        }
        return null;
    }

    private JSONObject createDirectConversation(String conversationId, String targetUserId) {
        String normalizedTarget = targetUserId == null ? "" : targetUserId.trim();
        if (normalizedTarget.isBlank()) {
            throw new IllegalArgumentException("conversation not found");
        }
        JSONObject target = userProfile(normalizedTarget);
        JSONObject conversation = new JSONObject()
                .put("conversationId", conversationId)
                .put("title", target.optString("nickname", "私信"))
                .put("avatarUrl", target.optString("avatarUrl", "stitch_ui/images/stitch-14.png"))
                .put("lastMessage", "")
                .put("timeText", "刚刚")
                .put("unreadCount", 0)
                .put("targetUserId", normalizedTarget)
                .put("participantUserIds", new JSONArray()
                        .put(session.requireCurrentUserId())
                        .put(normalizedTarget));
        conversations.put(conversation);
        return conversation;
    }

    private String requireKnownCurrentUser() {
        String userId = session.requireCurrentUserId();
        userProfile(userId);
        return userId;
    }

    private JSONObject userProfile(String userId) {
        JSONObject profileSeed = seedStore.documentCopy("profile.json");
        JSONObject current = profileSeed.getJSONObject("userProfile");
        if (userId.equals(current.optString("userId"))) {
            return current;
        }
        JSONArray users = profileSeed.optJSONArray("users");
        if (users != null) {
            for (int index = 0; index < users.length(); index++) {
                JSONObject user = users.getJSONObject(index);
                if (userId.equals(user.optString("userId"))) {
                    return user;
                }
            }
        }
        throw new IllegalArgumentException("user not found");
    }

    private static JSONObject page(String key, JSONArray source, int cursor, int limit) {
        JSONArray items = new JSONArray();
        int end = Math.min(source.length(), cursor + limit);
        for (int index = cursor; index < end; index++) {
            items.put(source.get(index));
        }
        return new JSONObject()
                .put("cursor", cursor == 0 ? "" : String.valueOf(cursor))
                .put("nextCursor", end < source.length() ? String.valueOf(end) : "")
                .put("hasMore", end < source.length())
                .put("totalCount", source.length())
                .put(key, items);
    }

    private static boolean contains(JSONArray values, String target) {
        for (int index = 0; index < values.length(); index++) {
            if (target.equals(values.optString(index))) {
                return true;
            }
        }
        return false;
    }

    private static JSONObject objectOrEmpty(String requestBody) {
        String normalized = requestBody == null ? "" : requestBody.trim();
        return normalized.isEmpty() ? new JSONObject() : new JSONObject(normalized);
    }

    private static JSONObject copy(JSONObject source) {
        return new JSONObject(source.toString());
    }

    private record Query(String rawQuery) {
        static Query from(URI uri) {
            return new Query(uri.getRawQuery());
        }

        String value(String key) {
            if (rawQuery == null || rawQuery.isBlank()) {
                return "";
            }
            String[] pairs = rawQuery.split("&");
            for (String pair : pairs) {
                int separator = pair.indexOf('=');
                String rawKey = separator >= 0 ? pair.substring(0, separator) : pair;
                if (key.equals(decode(rawKey))) {
                    return separator >= 0 ? decode(pair.substring(separator + 1)) : "";
                }
            }
            return "";
        }

        int cursor() {
            return positiveInt(value("cursor"), 0);
        }

        int limit(int defaultValue) {
            return Math.max(1, positiveInt(value("limit"), Math.max(1, defaultValue)));
        }
    }

    private static int positiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
