package com.nbn.backend.domain.ai;

import com.nbn.backend.store.JsonSeedStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AiApiService {
    private static final String SOURCE_RULE_FALLBACK = "rule_fallback";
    private static final String FALLBACK_NOT_CONFIGURED = "AI_PROVIDER_NOT_CONFIGURED";
    private static final String FALLBACK_PROVIDER_ERROR = "AI_PROVIDER_ERROR";
    private static final int DEFAULT_FALLBACK_RESULT_LIMIT = 5;

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
                "用户查询：" + query + "\n候选广告：" + candidateResultsJson()
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
        String normalizedQuery = normalize(query);
        List<JSONObject> rankedItems = new ArrayList<>();
        for (JSONObject feedItem : feedItemsByAdId.values()) {
            String adId = feedItem.optString("adId");
            if (requestedAdIds.length() > 0 && !contains(requestedAdIds, adId)) {
                continue;
            }
            double score = scoreFeedItem(feedItem, normalizedQuery);
            if (requestedAdIds.length() == 0 && !normalizedQuery.isBlank() && score <= 0.0) {
                continue;
            }
            rankedItems.add(resultFromFeedItem(feedItem, score, normalizedQuery));
        }

        if (rankedItems.isEmpty() && requestedAdIds.length() == 0) {
            int count = 0;
            for (JSONObject feedItem : feedItemsByAdId.values()) {
                rankedItems.add(resultFromFeedItem(feedItem, 0.45, normalizedQuery));
                count++;
                if (count >= DEFAULT_FALLBACK_RESULT_LIMIT) {
                    break;
                }
            }
        }

        rankedItems.sort(Comparator.comparingDouble(item -> -item.optDouble("rankScore", 0.0)));
        JSONArray ranked = new JSONArray();
        for (JSONObject item : rankedItems) {
            ranked.put(item);
        }
        return ranked;
    }

    private JSONArray candidateResultsJson() {
        JSONArray candidates = new JSONArray();
        for (JSONObject feedItem : feedItemsByAdId.values()) {
            candidates.put(resultFromFeedItem(feedItem, 0.5, ""));
        }
        return candidates;
    }

    private JSONObject resultFromFeedItem(JSONObject feedItem, double score, String normalizedQuery) {
        String adId = feedItem.optString("adId");
        JSONArray matchedTags = matchedTags(feedItem, normalizedQuery);
        JSONObject recommendationReason = new JSONObject()
                .put("summary", reasonSummary(feedItem, matchedTags))
                .put("matchedTags", matchedTags)
                .put("valueExplanation", feedItem.optString("subtitle", feedItem.optString("description", "")))
                .put("score", score);
        String imageUrl = "";
        JSONObject cover = feedItem.optJSONObject("cover");
        if (cover != null) {
            imageUrl = cover.optString("url", cover.optString("localAssetName", ""));
        }
        return new JSONObject()
                .put("adId", adId)
                .put("title", feedItem.optString("title"))
                .put("reason", reasonSummary(feedItem, matchedTags))
                .put("priceText", feedItem.optString("priceText", ""))
                .put("localAssetName", cover == null ? "" : cover.optString("localAssetName", imageUrl))
                .put("imageUrl", imageUrl)
                .put("ctaText", feedItem.optString("ctaText", "查看详情"))
                .put("recommendationReason", recommendationReason)
                .put("rankScore", score);
    }

    private static double scoreFeedItem(JSONObject feedItem, String normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            return 0.5;
        }
        double score = 0.0;
        if (normalize(feedItem.optString("title")).contains(normalizedQuery)) {
            score += 0.45;
        }
        if (normalize(feedItem.optString("brand")).contains(normalizedQuery)
                || normalize(feedItem.optString("category")).contains(normalizedQuery)) {
            score += 0.25;
        }
        if (normalize(feedItem.optString("subtitle")).contains(normalizedQuery)
                || normalize(feedItem.optString("description")).contains(normalizedQuery)) {
            score += 0.2;
        }
        JSONArray tags = feedItem.optJSONArray("tags");
        for (int index = 0; tags != null && index < tags.length(); index++) {
            String tag = tagName(tags.opt(index));
            String normalizedTag = normalize(tag);
            if (normalizedTag.isBlank()) {
                continue;
            }
            if (normalizedTag.contains(normalizedQuery) || normalizedQuery.contains(normalizedTag)) {
                score += 0.25;
            }
        }
        return Math.min(1.0, score);
    }

    private static JSONArray matchedTags(JSONObject feedItem, String normalizedQuery) {
        JSONArray matched = new JSONArray();
        JSONArray tags = feedItem.optJSONArray("tags");
        for (int index = 0; tags != null && index < tags.length(); index++) {
            String tag = tagName(tags.opt(index));
            if (tag.isBlank()) {
                continue;
            }
            String normalizedTag = normalize(tag);
            if (normalizedQuery.isBlank()
                    || normalizedTag.contains(normalizedQuery)
                    || normalizedQuery.contains(normalizedTag)) {
                matched.put(tag);
            }
        }
        return matched;
    }

    private static String reasonSummary(JSONObject feedItem, JSONArray matchedTags) {
        if (matchedTags.length() > 0) {
            List<String> tags = new ArrayList<>();
            for (int index = 0; index < Math.min(3, matchedTags.length()); index++) {
                tags.add(matchedTags.optString(index));
            }
            return "命中" + String.join("、", tags) + "，来自后端广告数据。";
        }
        String description = feedItem.optString("description", "");
        return description.isBlank() ? "来自后端广告数据的默认候选。" : description;
    }

    private static String tagName(Object tag) {
        if (tag instanceof JSONObject) {
            return ((JSONObject) tag).optString("name", "");
        }
        return tag == null ? "" : String.valueOf(tag);
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

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private record CloudText(String text, String fallbackReason) {
        boolean hasText() {
            return text != null && !text.isBlank();
        }
    }
}
