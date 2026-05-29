package com.nbn.adfeed.ui.search;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.nbn.adfeed.R;
import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.repository.AdRepository;

import java.util.ArrayList;
import java.util.List;

public final class SearchBottomSheetDialogFragment extends BottomSheetDialogFragment {
    private static final float SHEET_HEIGHT_RATIO = 0.9f;

    public interface OnSearchResultListener {
        void onSearchResult(List<String> matchedAdIds);
    }

    private final AdRepository adRepository = new MockAdRepository();

    private MessageAdapter messageAdapter;
    private EditText searchInput;
    private RecyclerView conversationList;

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
        conversationList = view.findViewById(R.id.searchConversationList);

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
        view.findViewById(R.id.searchSendButton).setOnClickListener(sendView -> sendCurrentMessage());
        searchInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentMessage();
                return true;
            }
            return false;
        });

        addAssistantMessage(getString(R.string.search_welcome_message));

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

    private void sendCurrentMessage() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            return;
        }

        messageAdapter.addMessage(new ChatMessage(query, true));

        List<AdItem> matchedAds = adRepository.searchByKeyword(query);
        List<String> matchedAdIds = toAdIds(matchedAds);
        if (matchedAdIds.isEmpty()) {
            addAssistantMessage(getString(R.string.search_no_results_reply));
        } else {
            addAssistantMessage(getString(R.string.search_results_reply, matchedAdIds.size()));
            notifySearchResult(matchedAdIds);
        }

        searchInput.setText("");
    }

    private static List<String> toAdIds(List<AdItem> ads) {
        List<String> ids = new ArrayList<>();
        for (AdItem ad : ads) {
            ids.add(ad.getId());
        }
        return ids;
    }

    private void notifySearchResult(List<String> matchedAdIds) {
        if (requireActivity() instanceof OnSearchResultListener) {
            ((OnSearchResultListener) requireActivity()).onSearchResult(matchedAdIds);
        }
    }

    private void addAssistantMessage(String text) {
        messageAdapter.addMessage(new ChatMessage(text, false));
        conversationList.post(() -> {
            if (conversationList.canScrollVertically(1)) {
                conversationList.scrollToPosition(messageAdapter.getItemCount() - 1);
            }
        });
    }
}
