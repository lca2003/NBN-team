package com.nbn.adfeed.data.remote;

import com.nbn.adfeed.data.model.stitch.StitchMessageModels;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BackendMessageDataSource {
    public interface Transport {
        String get(String path) throws RemoteAdException;

        String post(String path, String body) throws RemoteAdException;
    }

    public static final class NotificationPage {
        public final String cursor;
        public final String nextCursor;
        public final boolean hasMore;
        public final int totalCount;
        public final String type;
        public final List<StitchMessageModels.NotificationItem> items;

        NotificationPage(JSONObject data) {
            this.cursor = data.optString("cursor");
            this.nextCursor = data.optString("nextCursor");
            this.hasMore = data.optBoolean("hasMore", false);
            this.totalCount = Math.max(0, data.optInt("totalCount", 0));
            this.type = data.optString("type");
            this.items = parseNotifications(data.optJSONArray("notifications"));
        }
    }

    public static final class ConversationPage {
        public final String cursor;
        public final String nextCursor;
        public final boolean hasMore;
        public final int totalCount;
        public final List<StitchMessageModels.Conversation> items;

        ConversationPage(JSONObject data) {
            this.cursor = data.optString("cursor");
            this.nextCursor = data.optString("nextCursor");
            this.hasMore = data.optBoolean("hasMore", false);
            this.totalCount = Math.max(0, data.optInt("totalCount", 0));
            this.items = parseConversations(data.optJSONArray("conversations"));
        }
    }

    public static final class MessagePage {
        public final String cursor;
        public final String nextCursor;
        public final boolean hasMore;
        public final int totalCount;
        public final String conversationId;
        public final List<StitchMessageModels.Message> items;

        MessagePage(JSONObject data) {
            this.cursor = data.optString("cursor");
            this.nextCursor = data.optString("nextCursor");
            this.hasMore = data.optBoolean("hasMore", false);
            this.totalCount = Math.max(0, data.optInt("totalCount", 0));
            this.conversationId = data.optString("conversationId");
            this.items = parseMessages(data.optJSONArray("messages"));
        }
    }

    public static final class ReadResult {
        public final int readCount;
        public final StitchMessageModels.NotificationSummary notificationSummary;

        ReadResult(JSONObject data) {
            this.readCount = Math.max(0, data.optInt("readCount", 0));
            this.notificationSummary = parseNotificationSummary(data.optJSONObject("notificationSummary"));
        }
    }

    public static final class AppendMessageResult {
        public final StitchMessageModels.Message message;
        public final StitchMessageModels.Conversation conversation;

        AppendMessageResult(JSONObject data) throws RemoteAdException {
            this.message = parseMessage(BackendJson.requiredObject(data, "message", "message"));
            this.conversation = parseConversation(BackendJson.requiredObject(data, "conversation", "message"));
        }
    }

    private final Transport transport;

    public BackendMessageDataSource(Transport transport) {
        this.transport = transport == null ? defaultTransport() : transport;
    }

    public static BackendMessageDataSource defaultDataSource() {
        return new BackendMessageDataSource(defaultTransport());
    }

    public StitchMessageModels.NotificationSummary notificationSummary() throws RemoteAdException {
        JSONObject data = BackendJson.dataFromEnvelope(transport.get("/v1/notifications/summary"), "message");
        return parseNotificationSummary(data.optJSONObject("notificationSummary"));
    }

    public NotificationPage notifications(String type, String cursor, int limit) throws RemoteAdException {
        JSONObject data = BackendJson.dataFromEnvelope(
                transport.get(pathWithPage("/v1/notifications", cursor, limit, "type", type)),
                "message"
        );
        return new NotificationPage(data);
    }

    public ReadResult markNotificationsRead(List<String> notificationIds, String type) throws RemoteAdException {
        JSONObject body = BackendJson.object("type", BackendJson.safe(type));
        BackendJson.put(body, "notificationIds", BackendJson.stringArray(notificationIds));
        JSONObject data = BackendJson.dataFromEnvelope(
                transport.post("/v1/notifications/read", body.toString()),
                "message"
        );
        return new ReadResult(data);
    }

    public ReadResult markAllNotificationsRead() throws RemoteAdException {
        JSONObject data = BackendJson.dataFromEnvelope(
                transport.post("/v1/notifications/read-all", ""),
                "message"
        );
        return new ReadResult(data);
    }

    public ConversationPage conversations(String cursor, int limit) throws RemoteAdException {
        JSONObject data = BackendJson.dataFromEnvelope(
                transport.get(pathWithPage("/v1/conversations", cursor, limit, "", "")),
                "message"
        );
        return new ConversationPage(data);
    }

    public MessagePage messages(String conversationId, String cursor, int limit) throws RemoteAdException {
        JSONObject data = BackendJson.dataFromEnvelope(
                transport.get(pathWithPage(
                        "/v1/conversations/" + BackendJson.encode(conversationId) + "/messages",
                        cursor,
                        limit,
                        "",
                        ""
                )),
                "message"
        );
        return new MessagePage(data);
    }

    public AppendMessageResult appendMessage(
            String conversationId,
            String messageId,
            String senderUserId,
            String content,
            String messageType
    ) throws RemoteAdException {
        JSONObject body = BackendJson.object(
                "messageId", BackendJson.safe(messageId),
                "senderUserId", BackendJson.safe(senderUserId),
                "content", BackendJson.safe(content),
                "messageType", BackendJson.safe(messageType)
        );
        JSONObject data = BackendJson.dataFromEnvelope(
                transport.post(
                        "/v1/conversations/" + BackendJson.encode(conversationId) + "/messages",
                        body.toString()
                ),
                "message"
        );
        return new AppendMessageResult(data);
    }

    public StitchMessageModels.AiAssistantDigest aiAssistantDigest() throws RemoteAdException {
        JSONObject data = BackendJson.dataFromEnvelope(transport.get("/v1/ai-assistant/digest"), "message");
        JSONObject digest = BackendJson.requiredObject(data, "aiAssistantDigest", "message");
        return new StitchMessageModels.AiAssistantDigest(
                digest.optString("digestId"),
                digest.optString("title"),
                digest.optString("summary"),
                BackendJson.strings(digest.optJSONArray("recommendationAdIds")),
                digest.optString("generatedAtText")
        );
    }

    private static StitchMessageModels.NotificationSummary parseNotificationSummary(JSONObject summary) {
        JSONObject source = summary == null ? new JSONObject() : summary;
        return new StitchMessageModels.NotificationSummary(
                source.optInt("likeUnreadCount", 0),
                source.optInt("collectUnreadCount", 0),
                source.optInt("followerUnreadCount", 0),
                source.optInt("commentUnreadCount", 0)
        );
    }

    private static List<StitchMessageModels.NotificationItem> parseNotifications(JSONArray notificationsJson) {
        if (notificationsJson == null) {
            return Collections.emptyList();
        }
        List<StitchMessageModels.NotificationItem> items = new ArrayList<>();
        for (int index = 0; index < notificationsJson.length(); index++) {
            JSONObject notification = notificationsJson.optJSONObject(index);
            if (notification != null) {
                items.add(new StitchMessageModels.NotificationItem(
                        notification.optString("notificationId"),
                        notification.optString("type"),
                        notification.optString("sourceUserId"),
                        notification.optString("targetAdId"),
                        notification.optString("content"),
                        notification.optString("timeText"),
                        notification.optBoolean("read", false)
                ));
            }
        }
        return items;
    }

    private static List<StitchMessageModels.Conversation> parseConversations(JSONArray conversationsJson) {
        if (conversationsJson == null) {
            return Collections.emptyList();
        }
        List<StitchMessageModels.Conversation> items = new ArrayList<>();
        for (int index = 0; index < conversationsJson.length(); index++) {
            JSONObject conversation = conversationsJson.optJSONObject(index);
            if (conversation != null) {
                items.add(parseConversation(conversation));
            }
        }
        return items;
    }

    private static StitchMessageModels.Conversation parseConversation(JSONObject conversation) {
        return new StitchMessageModels.Conversation(
                conversation.optString("conversationId"),
                conversation.optString("avatarUrl"),
                conversation.optString("title"),
                conversation.optString("lastMessage"),
                conversation.optString("timeText"),
                conversation.optInt("unreadCount", 0),
                conversation.optBoolean("groupChat", false)
        );
    }

    private static List<StitchMessageModels.Message> parseMessages(JSONArray messagesJson) {
        if (messagesJson == null) {
            return Collections.emptyList();
        }
        List<StitchMessageModels.Message> items = new ArrayList<>();
        for (int index = 0; index < messagesJson.length(); index++) {
            JSONObject message = messagesJson.optJSONObject(index);
            if (message != null) {
                items.add(parseMessage(message));
            }
        }
        return items;
    }

    private static StitchMessageModels.Message parseMessage(JSONObject message) {
        return new StitchMessageModels.Message(
                message.optString("messageId"),
                message.optString("conversationId"),
                message.optString("senderUserId"),
                message.optString("content"),
                message.optString("timeText"),
                message.optString("messageType")
        );
    }

    private static String pathWithPage(String basePath, String cursor, int limit, String extraKey, String extraValue) {
        StringBuilder path = new StringBuilder(basePath).append('?');
        if (!BackendJson.safe(extraKey).isEmpty()) {
            path.append(BackendJson.encode(extraKey)).append('=').append(BackendJson.encode(extraValue)).append('&');
        }
        path.append("cursor=").append(BackendJson.encode(cursor));
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
                        ? new RemoteAdException(RemoteAdException.Reason.NETWORK, "Backend message API unavailable")
                        : lastException;
            }
        };
    }
}
