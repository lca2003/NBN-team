package com.nbn.adfeed.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nbn.adfeed.R;

import java.util.ArrayList;
import java.util.List;

public final class MessageAdapter extends RecyclerView.Adapter<MessageViewHolder> {
    private final List<ChatMessage> messages = new ArrayList<>();

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
        View messageView = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.chat_search_message, parent, false);
        return new MessageViewHolder(messageView);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        holder.bind(messages.get(position));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }
    // 用于从SQLite恢复历史记录
    public void submitMessages(List<ChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }
}
