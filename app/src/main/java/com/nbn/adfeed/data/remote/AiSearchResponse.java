package com.nbn.adfeed.data.remote;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AiSearchResponse {
    @SerializedName("data")
    private Data data;

    @SerializedName("answer")
    private String answer;

    @SerializedName("matchedAdIds")
    private List<String> matchedAdIds;

    @SerializedName("fallback")
    private Boolean fallback;

    public AiSearchResponse(String answer, List<String> matchedAdIds, boolean fallback) {
        this.data = Data.from(answer, matchedAdIds, fallback);
        this.answer = answer;
        this.matchedAdIds = copyOrEmpty(matchedAdIds);
        this.fallback = fallback;
    }

    private AiSearchResponse() {
    }

    public String getAnswer() {
        if (answer != null && !answer.trim().isEmpty()) {
            return answer.trim();
        }
        if (data == null) {
            return "";
        }
        String message = data.lastAssistantMessage();
        if (!message.isEmpty()) {
            return message;
        }
        return data.fallbackMessage();
    }

    public List<String> getMatchedAdIds() {
        if (matchedAdIds != null) {
            return Collections.unmodifiableList(copyOrEmpty(matchedAdIds));
        }
        if (data == null || data.results == null) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (Result result : data.results) {
            if (result != null && result.adId != null && !result.adId.trim().isEmpty()) {
                ids.add(result.adId);
            }
        }
        return Collections.unmodifiableList(ids);
    }

    public boolean isFallback() {
        if (fallback != null) {
            return fallback;
        }
        if (data == null || data.provider == null) {
            return true;
        }
        return !"remote_ai".equals(data.provider.source);
    }

    private static List<String> copyOrEmpty(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(values);
    }

    private static final class Data {
        @SerializedName("messages")
        private List<Message> messages;

        @SerializedName("results")
        private List<Result> results;

        @SerializedName("fallback")
        private Fallback fallback;

        @SerializedName("provider")
        private Provider provider;

        private static Data from(String answer, List<String> matchedAdIds, boolean fallback) {
            Data data = new Data();
            data.messages = Collections.singletonList(new Message("ai", answer));
            List<Result> results = new ArrayList<>();
            for (String adId : copyOrEmpty(matchedAdIds)) {
                results.add(new Result(adId));
            }
            data.results = results;
            data.fallback = new Fallback(fallback ? "AI搜索不可用，使用mock降级数据" : "");
            data.provider = new Provider(fallback ? "rule_fallback" : "remote_ai");
            return data;
        }

        private String lastAssistantMessage() {
            if (messages == null) {
                return "";
            }
            for (int index = messages.size() - 1; index >= 0; index--) {
                Message message = messages.get(index);
                if (message == null || message.content == null || message.content.trim().isEmpty()) {
                    continue;
                }
                String senderType = message.senderType == null ? "" : message.senderType.trim();
                if ("ai".equals(senderType) || "assistant".equals(senderType)) {
                    return message.content.trim();
                }
            }
            return "";
        }

        private String fallbackMessage() {
            if (fallback == null || fallback.message == null) {
                return "";
            }
            return fallback.message.trim();
        }
    }

    private static final class Message {
        @SerializedName("senderType")
        private String senderType;

        @SerializedName("content")
        private String content;

        private Message() {
        }

        private Message(String senderType, String content) {
            this.senderType = senderType;
            this.content = content;
        }
    }

    private static final class Result {
        @SerializedName("adId")
        private String adId;

        private Result() {
        }

        private Result(String adId) {
            this.adId = adId;
        }
    }

    private static final class Fallback {
        @SerializedName("message")
        private String message;

        private Fallback() {
        }

        private Fallback(String message) {
            this.message = message;
        }
    }

    private static final class Provider {
        @SerializedName("source")
        private String source;

        private Provider() {
        }

        private Provider(String source) {
            this.source = source;
        }
    }
}
