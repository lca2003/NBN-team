package com.nbn.adfeed.ui.search;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nbn.adfeed.R;

public class MessageViewHolder extends RecyclerView.ViewHolder {
    private final LinearLayout messageContainer;
    private final TextView messageText;

    public MessageViewHolder(@NonNull View itemView) {
        super(itemView);
        messageContainer = itemView.findViewById(R.id.messageContainer);        //控制布局左右
        messageText = itemView.findViewById(R.id.messageText);                  //填充内容
    }

    public void bind(ChatMessage message) {
        messageText.setText(message.getText());
        if (message.isFromUser()) {
            messageContainer.setGravity(Gravity.END);
            messageText.setBackgroundResource(R.drawable.bubble_sent);
        } else {
            messageContainer.setGravity(Gravity.START);
            messageText.setBackgroundResource(R.drawable.bubble_received);
        }
    }
}
