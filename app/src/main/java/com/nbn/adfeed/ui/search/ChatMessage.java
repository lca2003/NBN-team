package com.nbn.adfeed.ui.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//存文本内容以及信息发送者，只有两种
public class ChatMessage {
    private long id;
    private String text;
    private boolean fromUser;
    private long createdAt;
    private List<String> matchedAdIds;

    public ChatMessage(String text, boolean fromUser) {
        this(0L, text, fromUser, System.currentTimeMillis(), Collections.emptyList());
    }

    public ChatMessage(String text, boolean fromUser, List<String> matchedAdIds) {
        this(0L, text, fromUser, System.currentTimeMillis(), matchedAdIds);
    }

    public ChatMessage(long id,
                       String text,
                       boolean fromUser,
                       long createdAt,
                       List<String> matchedAdIds) {
        this.id = id;
        this.text = text == null ? "" : text;
        this.fromUser = fromUser;
        this.createdAt = createdAt;
        setMatchedAdIds(matchedAdIds);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public String getText() {
        return text;
    }

    public void setFromUser(boolean fromUser) {
        this.fromUser = fromUser;
    }

    public boolean isFromUser() {
        return fromUser;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public List<String> getMatchedAdIds() {
        return matchedAdIds;
    }

    public void setMatchedAdIds(List<String> matchedAdIds) {
        if (matchedAdIds == null) {
            this.matchedAdIds = Collections.emptyList();
            return;
        }
        this.matchedAdIds = Collections.unmodifiableList(new ArrayList<>(matchedAdIds));
    }
}
