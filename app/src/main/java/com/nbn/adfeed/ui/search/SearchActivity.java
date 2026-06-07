package com.nbn.adfeed.ui.search;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nbn.adfeed.R;



public final class SearchActivity extends AppCompatActivity {
    private MessageAdapter messageAdapter;
    private EditText searchInput;
    private RecyclerView conversationList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        // 输入框与对话列表
        searchInput = findViewById(R.id.searchInput);
        conversationList = findViewById(R.id.searchConversationList);
        //初始化聊天列表
        messageAdapter = new MessageAdapter();
        conversationList.setLayoutManager(new LinearLayoutManager(this));
        conversationList.setAdapter(messageAdapter);

        //按钮绑定对应事件
        findViewById(R.id.searchCloseButton).setOnClickListener(view -> finish());
        findViewById(R.id.searchSendButton).setOnClickListener(view -> sendCurrentMessage());
        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentMessage();
                return true;
            }
            return false;
        });

        addAssistantMessage(getString(R.string.search_welcome_message));
    }


    private void sendCurrentMessage() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            return;
        }
        // 加入聊天记录
        messageAdapter.addMessage(new ChatMessage(query, true));

        addAssistantMessage(getString(R.string.search_mock_reply, query));

        searchInput.setText("");
    }

    // TODO 搜索广告实现在这里插入
    private void addAssistantMessage(String text) {
        messageAdapter.addMessage(new ChatMessage(text, false));
        conversationList.scrollToPosition(messageAdapter.getItemCount() - 1);
    }



}
