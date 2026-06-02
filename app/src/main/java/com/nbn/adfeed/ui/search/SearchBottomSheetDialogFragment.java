package com.nbn.adfeed.ui.search;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.nbn.adfeed.R;
import com.nbn.adfeed.ai.search.AiSearchResult;
import com.nbn.adfeed.ai.search.AiSearchService;
import com.nbn.adfeed.ai.search.RemoteAiSearchService;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// AI 搜索弹窗的控制器，负责 UI、发送搜索、恢复/保存聊天记录，以及把搜索命中的广告 ID 通知给外层
public final class SearchBottomSheetDialogFragment extends BottomSheetDialogFragment {
    private static final float SHEET_HEIGHT_RATIO = 0.9f;

    public interface OnSearchResultListener {
        void onSearchResult(List<String> matchedAdIds);
    }

    private final AiSearchService aiSearchService = new RemoteAiSearchService();

    private MessageAdapter messageAdapter;
    private EditText searchInput;
    private Button searchSendButton;
    private RecyclerView conversationList;
    private ChatMessageStore messageStore;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService persistenceExecutor;
    private boolean searchInProgress;
    private boolean historyLoaded;

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (!(dialog instanceof BottomSheetDialog)) {
            return;
        }

        BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialog;
        Window window = bottomSheetDialog.getWindow();
        if (window != null) {
            // 半透明
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0f);
            // 设置软键盘输入模式为 RESIZE：键盘弹出时会压缩布局空间，而不是平移整个窗口；保证顶部标题栏不被顶出屏幕，同时输入栏自然上移
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        // 找到 BottomSheet 中真正的内容容器（design_bottom_sheet）
        View sheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) {
            return;
        }
        // 使 sheet 背景透明
        sheet.setBackgroundColor(Color.TRANSPARENT);
        // // 获取并配置 BottomSheetBehavior，用于控制弹窗的展开/折叠行为
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
        behavior.setFitToContents(true);

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int peekHeight = (int) (screenHeight * 0.88f);  // 占屏幕 88%
        behavior.setPeekHeight(peekHeight, true);
        behavior.setSkipCollapsed(false);   // 允许折叠
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        // 将布局文件 inflate 成 View 对象并返回
        return inflater.inflate(R.layout.bottom_sheet_search, container, false);
    }

    //当视图创建完成后调用，负责初始化 UI 组件、设置适配器、绑定事件
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        //按钮
        searchInput = view.findViewById(R.id.searchInput);
        searchSendButton = view.findViewById(R.id.searchSendButton);
        conversationList = view.findViewById(R.id.searchConversationList);
        messageStore = new ChatMessageStore(requireContext());
        persistenceExecutor = Executors.newSingleThreadExecutor();

        messageAdapter = new MessageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.VERTICAL,
                false
        );
        conversationList.setLayoutManager(layoutManager);
        conversationList.setAdapter(messageAdapter);


        //关闭、点击发送、回车发送功能绑定
        view.findViewById(R.id.searchCloseButton).setOnClickListener(closeView -> dismiss());
        searchSendButton.setOnClickListener(sendView -> sendCurrentMessage());
        searchInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentMessage();
                return true;
            }
            return false;
        });
        //载入聊天对话历史
        loadHistory();

        // 强制弹窗初始高度为屏幕的 90%
       view.post(() -> {
           int screenHeight = getResources().getDisplayMetrics().heightPixels;
           int targetSheetHeight = (int) (screenHeight * SHEET_HEIGHT_RATIO); // 0.9f

           View titleBar = view.findViewById(R.id.titleBar);
           View inputBar = view.findViewById(R.id.searchInputBar);
           int titleHeight = titleBar.getHeight();
           int inputHeight = inputBar.getHeight();

           // 计算 RecyclerView 需要的最小高度 = 弹窗总高 - 标题栏 - 输入栏
           // 即使消息列表内容很少，RecyclerView最小高度会撑起足够空间
           int minRecyclerHeight = targetSheetHeight - titleHeight - inputHeight;
           if (minRecyclerHeight > 0) {
               conversationList.setMinimumHeight(minRecyclerHeight);
           }
       });
    }

    // 发送HTTP请求给后端大模型 POST /api/ai/search
    private void sendCurrentMessage() {
        //历史未载入则初始化
        if (!historyLoaded) {
            return;
        }
        // 已有搜索进行中，禁止重复发送
        if (searchInProgress) {
            return;
        }

        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            return;
        }

        addPersistentMessage(new ChatMessage(query, true));
        searchInput.setText("");
        setSearchInProgress(true);

        // 异步执行 AI 搜索
        aiSearchService.search(query, result -> {
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (isAdded() && getView() != null && messageAdapter != null) {
                    handleSearchResult(result);
                }
            });
        });
    }

        //处理 AI 搜索返回的结果
    private void handleSearchResult(AiSearchResult result) {
        setSearchInProgress(false);

        // 处理回答文本
        boolean hasAnswer = result.getAnswer() != null && !result.getAnswer().trim().isEmpty();
        List<String> matchedAdIds = result.getMatchedAdIds();
        String assistantText = hasAnswer ? result.getAnswer() : null;
        if (matchedAdIds.isEmpty()) {
            // 无匹配广告：如果没有回答，则显示默认的无结果文案
            if (assistantText == null) {
                assistantText = getString(R.string.search_no_results_reply);
            }
            addPersistentMessage(new ChatMessage(assistantText, false));
            return;
        }

        // 有匹配广告：如果没有回答，则显示包含匹配数量的结果提示
        if (assistantText == null) {
            assistantText = getString(R.string.search_results_reply, matchedAdIds.size());
        }
        addPersistentMessage(new ChatMessage(assistantText, false, matchedAdIds));
        // 将匹配的广告 ID 通知给外部（例如展示广告列表）
        notifySearchResult(matchedAdIds);
    }

    //设置搜索进行中状态，并同步更新输入框和发送按钮的可用性
    private void setSearchInProgress(boolean inProgress) {
        searchInProgress = inProgress;
        updateInputEnabled();
    }

    private void updateInputEnabled() {
        if (searchInput == null || searchSendButton == null) {
            return;
        }
        boolean enabled = historyLoaded && !searchInProgress;
        searchInput.setEnabled(enabled);
        searchSendButton.setEnabled(enabled);
    }

    private void notifySearchResult(List<String> matchedAdIds) {
        if (requireActivity() instanceof OnSearchResultListener) {
            ((OnSearchResultListener) requireActivity()).onSearchResult(matchedAdIds);
        }
    }

    //从SQLite载入历史
    private void loadHistory() {
        //暂时禁用输入框和发送按钮，避免用户在历史记录还没恢复时发送消息
        historyLoaded = false;
        updateInputEnabled();
        // 拿到子线程执行器和 SQLite 存储对象
        ExecutorService executor = persistenceExecutor;
        if (executor == null) {
            return;
        }
        ChatMessageStore store = messageStore;
        if (store == null) {
            return;
        }

        executor.execute(() -> {
            List<ChatMessage> messages;
            try {
                //读取SQLite
                messages = store.loadAll();
            } catch (RuntimeException exception) {
                messages = Collections.emptyList();
            }
            List<ChatMessage> loadedMessages = messages;
            //更新ui
            mainHandler.post(() -> {
                if (!isAdded() || getView() == null || messageAdapter == null) {
                    return;
                }
                historyLoaded = true;
                // 更新聊天列表
                messageAdapter.submitMessages(loadedMessages);
                if (loadedMessages.isEmpty()) {
                    addAssistantMessage(getString(R.string.search_welcome_message));
                } else {
                    // 有聊天记录，滚动到聊天记录底部查看最新
                    scrollConversationToBottom();
                }
                updateInputEnabled();
            });
        });
    }

    private void addPersistentMessage(ChatMessage message) {
        addMessageToUi(message);
        persistMessage(message);
    }

    private void persistMessage(ChatMessage message) {
        ExecutorService executor = persistenceExecutor;
        if (executor == null || executor.isShutdown()) {
            return;
        }
        ChatMessageStore store = messageStore;
        if (store == null) {
            return;
        }
        executor.execute(() -> {
            try {
                long id = store.insert(message);
                message.setId(id);
            } catch (RuntimeException ignored) {
                // 聊天展示不能被本地持久化失败阻断。
            }
        });
    }

    private void addAssistantMessage(String text) {
        addMessageToUi(new ChatMessage(text, false));
    }

    private void addMessageToUi(ChatMessage message) {
        if (messageAdapter == null || conversationList == null) {
            return;
        }
        messageAdapter.addMessage(message);
        scrollConversationToBottom();
    }

    private void scrollConversationToBottom() {
        if (conversationList == null || messageAdapter == null) {
            return;
        }
        conversationList.post(() -> {
            if (conversationList != null && messageAdapter != null && conversationList.canScrollVertically(1)) {
                conversationList.scrollToPosition(messageAdapter.getItemCount() - 1);
            }
        });
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacksAndMessages(null);
        ExecutorService executor = persistenceExecutor;
        ChatMessageStore store = messageStore;
        persistenceExecutor = null;
        if (executor != null && !executor.isShutdown()) {
            executor.execute(() -> {
                if (store != null) {
                    store.close();
                }
            });
            executor.shutdown();
        }
        messageStore = null;
        messageAdapter = null;
        searchInput = null;
        searchSendButton = null;
        conversationList = null;
        super.onDestroyView();
    }
}
