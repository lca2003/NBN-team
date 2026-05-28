package com.nbn.adfeed.ui.search;

//存文本内容以及信息发送者，只有两种
public class ChatMessage {
    private String text;

    private boolean fromUser;
    public ChatMessage(String text, boolean fromUser) {
        this.text = text;
        this.fromUser = fromUser;
    }

    public void setText(String text) {
        this.text = text;
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
}
