package com.nbn.backend.domain.ai;

import com.nbn.backend.store.JsonSeedStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class AiApiService {
    private static final String SOURCE_RULE_FALLBACK = "rule_fallback";
    private static final String FALLBACK_NOT_CONFIGURED = "AI_PROVIDER_NOT_CONFIGURED";
    private static final String FALLBACK_PROVIDER_ERROR = "AI_PROVIDER_ERROR";

    private final JsonSeedStore seedStore;
    private final CloudAiProvider cloudProvider;
    private final JSONObject searchSeed;
    private final Map<String, JSONObject> feedItemsByAdId = new LinkedHashMap<>();
    private final Map<String, JSONObject> detailsByAdId = new LinkedHashMap<>();
    private final Map<String, JSONObject> sessionsById = new LinkedHashMap<>();

    public AiApiService(JsonSeedStore seedStore) {
        this(seedStore, CloudAiProvider.fromEnvironment());
    }

    public AiApiService(JsonSeedStore seedStore, CloudAiProvider cloudProvider) {
        this.seedStore = seedStore;
        this.cloudProvider = cloudProvider;
        this.searchSeed = seedStore.documentCopy("search_results.json");
        JSONObject homeFeed = seedStore.documentCopy("home_feed.json");
        JSONArray feedItems = homeFeed.getJSONObject("page").getJSONArray("items");
        for (int index = 0; index < feedItems.length(); index++) {
            JSONObject item = feedItems.getJSONObject(index);
            feedItemsByAdId.put(item.getString("adId"), item);
        }
        JSONObject detailSeed = seedStore.documentCopy("ad_details.json");
        JSONArray details = detailSeed.getJSONArray("details");
        for (int index = 0; index < details.length(); index++) {
            JSONObject detail = details.getJSONObject(index);
            detailsByAdId.put(detail.getString("adId"), detail);
        }
        JSONObject seedSession = new JSONObject();
        seedSession.put("session", copy(searchSeed.getJSONObject("session")));
        seedSession.put("messages", new JSONArray(searchSeed.getJSONArray("messages").toString()));
        seedSession.put("results", new JSONArray(searchSeed.getJSONArray("results").toString()));
        seedSession.put("fallback", fallbackJson(FALLBACK_NOT_CONFIGURED));
        sessionsById.put(seedSession.getJSONObject("session").getString("sessionId"), seedSession);
        JSONArray persistedSessions = searchSeed.optJSONArray("sessions");
        if (persistedSessions != null) {
            for (int index = 0; index < persistedSessions.length(); index++) {
                JSONObject persistedSession = persistedSessions.getJSONObject(index);
                sessionsById.put(
                        persistedSession.getJSONObject("session").getString("sessionId"),
                        persistedSession
                );
            }
        }
    }

    public synchronized String suggestionsJson() {
        JSONObject data = new JSONObject();
        data.put("suggestions", new JSONArray(searchSeed.getJSONArray("suggestions").toString()));
        return data.toString();
    }

    public synchronized String searchJson(String requestBody) {
        JSONObject body = objectOrEmpty(requestBody);
        String query = body.optString("query", body.optString("userQuery", ""));
        if (query.isBlank()) {
            query = searchSeed.getJSONObject("session").optString("userQuery", "智能推荐");
        }
        String sessionId = body.optString("sessionId", "search_session_" + UUID.randomUUID());
        long now = System.currentTimeMillis();
        CloudText cloudText = cloudText(
                "你是广告推荐助手。用一句中文解释推荐逻辑，不超过 60 字。",
                "用户查询：" + query + "\n候选广告：" + searchSeed.getJSONArray("results")
        );
        JSONObject session = new JSONObject();
        session.put("sessionId", sessionId);
        session.put("userQuery", query);
        session.put("createdAtMillis", now);
        session.put("context", body.optString("context", "backend_ai_search"));

        JSONArray messages = new JSONArray();
        messages.put(message("msg_user_" + UUID.randomUUID(), sessionId, "user", query, "text", now));
        messages.put(message(
                "msg_ai_" + UUID.randomUUID(),
                sessionId,
                "ai",
                cloudText.hasText()
                        ? cloudText.text()
                        : "已基于后端规则 fallback 生成推荐，云端模型未配置或失败时不会阻塞搜索。",
                "assistant_summary",
                now + 500
        ));

        JSONObject data = new JSONObject();
        data.put("session", session);
        data.put("messages", messages);
        data.put("results", rankedResults(query, optionalAdIds(body)));
        data.put("fallback", fallbackJson(cloudText.fallbackReason()));
        data.put("provider", providerJson(cloudText));
        sessionsById.put(sessionId, copy(data));
        persistSearchState();
        return data.toString();
    }

    public synchronized String sessionJson(String sessionId) {
        JSONObject session = sessionsById.get(sessionId);
        return session == null ? null : copy(session).toString();
    }

    public synchronized String appendMessageJson(String sessionId, String requestBody) {
        JSONObject session = sessionsById.get(sessionId);
        if (session == null) {
            return null;
        }
        JSONObject body = objectOrEmpty(requestBody);
        String content = body.optString("content", body.optString("query", ""));
        long now = System.currentTimeMillis();
        JSONArray messages = session.getJSONArray("messages");
        messages.put(message("msg_user_" + UUID.randomUUID(), sessionId, "user", content, "text", now));
        messages.put(message(
                "msg_ai_" + UUID.randomUUID(),
                sessionId,
                "ai",
                "已收到补充条件，并用后端规则 fallback 重新解释推荐理由。",
                "assistant_summary",
                now + 500
        ));
        session.put("messages", messages);
        session.put("results", rankedResults(content, new JSONArray()));
        session.put("fallback", fallbackJson(FALLBACK_NOT_CONFIGURED));
        persistSearchState();
        return copy(session).toString();
    }

    public synchronized String summaryJson(String adId) {
        JSONObject detail = detailsByAdId.get(adId);
        JSONObject feedItem = feedItemsByAdId.get(adId);
        if (detail == null && feedItem == null) {
            return null;
        }
        String title = detail != null ? detail.optString("title") : feedItem.optString("title");
        String insight = detail != null
                ? detail.optString("aiDeepInsight")
                : feedItem.optString("description");
        CloudText cloudText = cloudText(
                "你是广告摘要模型。为广告生成不超过 40 个中文字符的摘要，只输出摘要正文。",
                "标题：" + title + "\n广告信息：" + insight
        );
        JSONObject data = new JSONObject();
        data.put("adId", adId);
        data.put("title", title);
        data.put("summary", cloudText.hasText() ? cloudText.text() : insight);
        data.put("source", cloudText.hasText() ? CloudAiProvider.SOURCE : SOURCE_RULE_FALLBACK);
        data.put("fallbackReason", cloudText.hasText() ? "" : cloudText.fallbackReason());
        data.put("cloudConfigured", cloudConfigured());
        return data.toString();
    }

    public synchronized String tagsJson(String adId) {
        JSONObject feedItem = feedItemsByAdId.get(adId);
        if (feedItem == null) {
            return null;
        }
        CloudText cloudText = cloudText(
                "你是广告标签分类模型。只输出 JSON 数组，包含 3 到 5 个中文短标签。",
                "广告标题：" + feedItem.optString("title") + "\n描述：" + feedItem.optString("description")
        );
        JSONObject data = new JSONObject();
        data.put("adId", adId);
        JSONArray cloudTags = parseTags(cloudText.text());
        boolean hasCloudTags = cloudTags.length() > 0;
        data.put("tags", hasCloudTags ? cloudTags : new JSONArray(feedItem.getJSONArray("tags").toString()));
        data.put("source", hasCloudTags ? CloudAiProvider.SOURCE : SOURCE_RULE_FALLBACK);
        data.put("fallbackReason", hasCloudTags ? "" : cloudText.fallbackReason());
        data.put("cloudConfigured", cloudConfigured());
        return data.toString();
    }

    public synchronized String rerankJson(String requestBody) {
        JSONObject body = objectOrEmpty(requestBody);
        JSONArray requestedAdIds = optionalAdIds(body);
        JSONObject data = new JSONObject();
        data.put("items", rankedResults(body.optString("query", ""), requestedAdIds));
        data.put("source", SOURCE_RULE_FALLBACK);
        data.put("fallbackReason", FALLBACK_NOT_CONFIGURED);
        return data.toString();
    }

    private JSONArray rankedResults(String query, JSONArray requestedAdIds) {
        JSONArray seedResults = searchSeed.getJSONArray("results");
        JSONArray ranked = new JSONArray();
        for (int index = 0; index < seedResults.length(); index++) {
            JSONObject result = copy(seedResults.getJSONObject(index));
            if (requestedAdIds.length() > 0 && !contains(requestedAdIds, result.optString("adId"))) {
                continue;
            }
            double score = result.getJSONObject("recommendationReason").optDouble("score", 0.5);
            if (!query.isBlank() && matchesQuery(result, query)) {
                score = Math.min(1.0, score + 0.03);
            }
            result.put("rankScore", score);
            ranked.put(result);
        }
        if (ranked.length() == 0 && requestedAdIds.length() > 0) {
            for (int index = 0; index < requestedAdIds.length(); index++) {
                String adId = requestedAdIds.optString(index);
                JSONObject feedItem = feedItemsByAdId.get(adId);
                if (feedItem != null) {
                    ranked.put(new JSONObject()
                            .put("adId", adId)
                            .put("title", feedItem.optString("title"))
                            .put("reason", "命中后端 fallback 候选集")
                            .put("rankScore", 0.5));
                }
            }
        }
        return ranked;
    }

    private JSONObject fallbackJson(String fallbackReason) {
        return new JSONObject(searchSeed.getJSONObject("fallback").toString())
                .put("fallbackReason", fallbackReason)
                .put("source", SOURCE_RULE_FALLBACK);
    }

    private JSONObject providerJson(CloudText cloudText) {
        return cloudProvider.metadataJson()
                .put("source", cloudText.hasText() ? CloudAiProvider.SOURCE : SOURCE_RULE_FALLBACK)
                .put("fallbackReason", cloudText.hasText() ? "" : cloudText.fallbackReason());
    }

    private void persistSearchState() {
        JSONArray sessions = new JSONArray();
        for (JSONObject session : sessionsById.values()) {
            sessions.put(copy(session));
        }
        seedStore.writeState("search_results.json", copy(searchSeed).put("sessions", sessions));
    }

    private CloudText cloudText(String systemPrompt, String userPrompt) {
        if (!cloudProvider.configured()) {
            return new CloudText("", FALLBACK_NOT_CONFIGURED);
        }
        try {
            String text = cloudProvider.completeText(systemPrompt, userPrompt);
            if (text.isBlank()) {
                return new CloudText("", FALLBACK_PROVIDER_ERROR);
            }
            return new CloudText(text, "");
        } catch (RuntimeException exception) {
            return new CloudText("", FALLBACK_PROVIDER_ERROR);
        }
    }

    private static JSONArray parseTags(String text) {
        if (text == null || text.isBlank()) {
            return new JSONArray();
        }
        String normalized = text.trim();
        try {
            if (normalized.startsWith("[")) {
                return new JSONArray(normalized);
            }
        } catch (RuntimeException exception) {
            return new JSONArray();
        }
        JSONArray tags = new JSONArray();
        String[] parts = normalized.replace("，", ",").replace("、", ",").split(",");
        for (String part : parts) {
            String tag = part.trim();
            if (!tag.isBlank()) {
                tags.put(tag);
            }
            if (tags.length() >= 5) {
                break;
            }
        }
        return tags;
    }

    private static JSONObject message(
            String messageId,
            String sessionId,
            String senderType,
            String content,
            String messageType,
            long createdAtMillis
    ) {
        return new JSONObject()
                .put("messageId", messageId)
                .put("sessionId", sessionId)
                .put("senderType", senderType)
                .put("content", content)
                .put("messageType", messageType)
                .put("createdAtMillis", createdAtMillis);
    }

    private static boolean matchesQuery(JSONObject result, String query) {
        String normalizedQuery = query.toLowerCase();
        String haystack = (result.optString("title") + " " + result.optString("reason") + " "
                + result.optJSONObject("recommendationReason")).toLowerCase();
        return haystack.contains(normalizedQuery);
    }

    private static JSONArray optionalAdIds(JSONObject body) {
        JSONArray adIds = body.optJSONArray("adIds");
        return adIds == null ? new JSONArray() : adIds;
    }

    private static boolean contains(JSONArray array, String value) {
        for (int index = 0; index < array.length(); index++) {
            if (value.equals(array.optString(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean cloudConfigured() {
        return cloudProvider.configured();
    }

    private static JSONObject objectOrEmpty(String requestBody) {
        String normalized = requestBody == null ? "" : requestBody.trim();
        return normalized.isEmpty() ? new JSONObject() : new JSONObject(normalized);
    }

    private static JSONObject copy(JSONObject source) {
        return new JSONObject(source.toString());
    }

    private record CloudText(String text, String fallbackReason) {
        boolean hasText() {
            return text != null && !text.isBlank();
        }
    }
}
