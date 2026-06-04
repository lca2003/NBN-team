package com.nbn.adfeed.data.remote;

import com.nbn.adfeed.data.model.stitch.StitchMessageModels;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class BackendMessageDataSourceTest {
    @Test
    public void messageClientCallsNotificationConversationAndDigestRoutes() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        BackendMessageDataSource dataSource = new BackendMessageDataSource(transport);

        StitchMessageModels.NotificationSummary summary = dataSource.notificationSummary();
        BackendMessageDataSource.NotificationPage notifications = dataSource.notifications("like", "", 10);
        BackendMessageDataSource.ReadResult readResult = dataSource.markNotificationsRead(
                List.of("nt_like_001"),
                ""
        );
        BackendMessageDataSource.ReadResult readAllResult = dataSource.markAllNotificationsRead();
        BackendMessageDataSource.ConversationPage conversations = dataSource.conversations("", 10);
        BackendMessageDataSource.MessagePage messages = dataSource.messages("conv_ai_assistant", "", 10);
        BackendMessageDataSource.AppendMessageResult appendResult = dataSource.appendMessage(
                "conv_ai_assistant",
                "dm_client_001",
                "user_me_001",
                "收到",
                "text"
        );
        StitchMessageModels.AiAssistantDigest digest = dataSource.aiAssistantDigest();

        assertEquals(2, summary.likeUnreadCount);
        assertEquals("like", notifications.type);
        assertEquals(1, notifications.items.size());
        assertEquals(1, readResult.readCount);
        assertEquals(2, readAllResult.readCount);
        assertEquals(1, conversations.items.size());
        assertEquals("conv_ai_assistant", messages.conversationId);
        assertEquals("dm_client_001", appendResult.message.messageId);
        assertEquals("AI 广告助手", appendResult.conversation.title);
        assertEquals("digest_week", digest.digestId);
        assertEquals("GET /v1/notifications/summary", transport.calls.get(0));
        assertEquals("GET /v1/notifications?type=like&cursor=&limit=10", transport.calls.get(1));
        assertEquals("POST /v1/notifications/read", transport.calls.get(2));
        assertEquals("POST /v1/notifications/read-all", transport.calls.get(3));
        assertEquals("GET /v1/conversations?cursor=&limit=10", transport.calls.get(4));
        assertEquals("GET /v1/conversations/conv_ai_assistant/messages?cursor=&limit=10", transport.calls.get(5));
        assertEquals("POST /v1/conversations/conv_ai_assistant/messages", transport.calls.get(6));
        assertEquals("GET /v1/ai-assistant/digest", transport.calls.get(7));
        assertFalse(transport.requestBodies.get(2).isBlank());
        assertFalse(transport.requestBodies.get(6).isBlank());
    }

    @Test(expected = RemoteAdException.class)
    public void nonOkEnvelopeThrowsRemoteException() throws Exception {
        BackendMessageDataSource dataSource = new BackendMessageDataSource(new RecordingTransport() {
            @Override
            public String get(String path) {
                return "{\"requestId\":\"req-test\",\"code\":\"REMOTE_ERROR\",\"message\":\"down\",\"data\":null}";
            }
        });

        dataSource.notificationSummary();
    }

    private static class RecordingTransport implements BackendMessageDataSource.Transport {
        private final java.util.List<String> calls = new java.util.ArrayList<>();
        private final java.util.List<String> requestBodies = new java.util.ArrayList<>();

        @Override
        public String get(String path) {
            calls.add("GET " + path);
            requestBodies.add("");
            if (path.startsWith("/v1/notifications/summary")) {
                return ok("{\"notificationSummary\":" + summaryJson() + "}");
            }
            if (path.startsWith("/v1/notifications?")) {
                return ok("{\"cursor\":\"\",\"nextCursor\":\"\",\"hasMore\":false,\"totalCount\":1,"
                        + "\"type\":\"like\",\"notifications\":[{\"notificationId\":\"nt_like_001\","
                        + "\"type\":\"like\",\"sourceUserId\":\"user_1\",\"targetAdId\":\"ad_001\","
                        + "\"content\":\"赞了你\",\"timeText\":\"刚刚\",\"read\":false}]}");
            }
            if (path.startsWith("/v1/conversations/")) {
                return ok("{\"cursor\":\"\",\"nextCursor\":\"\",\"hasMore\":false,\"totalCount\":1,"
                        + "\"conversationId\":\"conv_ai_assistant\",\"messages\":[{\"messageId\":\"dm_001\","
                        + "\"conversationId\":\"conv_ai_assistant\",\"senderUserId\":\"ai_assistant\","
                        + "\"content\":\"摘要\",\"timeText\":\"刚刚\",\"messageType\":\"text\"}]}");
            }
            if (path.startsWith("/v1/conversations")) {
                return ok("{\"cursor\":\"\",\"nextCursor\":\"\",\"hasMore\":false,\"totalCount\":1,"
                        + "\"conversations\":[" + conversationJson() + "]}");
            }
            return ok("{\"aiAssistantDigest\":{\"digestId\":\"digest_week\",\"title\":\"周报\","
                    + "\"summary\":\"互动增长\",\"recommendationAdIds\":[\"ad_001\"],"
                    + "\"generatedAtText\":\"今天\"}}");
        }

        @Override
        public String post(String path, String body) {
            calls.add("POST " + path);
            requestBodies.add(body == null ? "" : body);
            if (path.endsWith("/read")) {
                return ok("{\"readCount\":1,\"notificationSummary\":" + summaryJson() + "}");
            }
            if (path.endsWith("/read-all")) {
                return ok("{\"readCount\":2,\"notificationSummary\":" + summaryJson() + "}");
            }
            return ok("{\"message\":{\"messageId\":\"dm_client_001\",\"conversationId\":\"conv_ai_assistant\","
                    + "\"senderUserId\":\"user_me_001\",\"content\":\"收到\",\"timeText\":\"刚刚\","
                    + "\"messageType\":\"text\"},\"conversation\":" + conversationJson() + "}");
        }

        private static String ok(String dataJson) {
            return "{\"requestId\":\"req-test\",\"code\":\"OK\",\"message\":\"ok\",\"data\":" + dataJson + "}";
        }

        private static String summaryJson() {
            return "{\"likeUnreadCount\":2,\"collectUnreadCount\":1,"
                    + "\"followerUnreadCount\":0,\"commentUnreadCount\":3}";
        }

        private static String conversationJson() {
            return "{\"conversationId\":\"conv_ai_assistant\",\"avatarUrl\":\"avatar\","
                    + "\"title\":\"AI 广告助手\",\"lastMessage\":\"收到\",\"timeText\":\"刚刚\","
                    + "\"unreadCount\":1,\"groupChat\":false}";
        }
    }
}
