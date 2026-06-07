package com.nbn.adfeed.ui.feed;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nbn.adfeed.R;

/**
 * 信息流列表底部 footer ViewHolder，支持四态切换。
 *
 * <ul>
 *   <li>{@link FooterState#HIDDEN} — 完全隐藏。</li>
 *   <li>{@link FooterState#LOADING} — 显示加载中转圈。</li>
 *   <li>{@link FooterState#NO_MORE} — 显示"没有更多了"。</li>
 *   <li>{@link FooterState#ERROR} — 显示"加载失败，点击重试"，点击触发重试回调。</li>
 * </ul>
 */
final class FeedFooterViewHolder extends RecyclerView.ViewHolder {

    private final View loadingView;
    private final TextView textView;

    FeedFooterViewHolder(@NonNull View itemView) {
        super(itemView);
        loadingView = itemView.findViewById(R.id.footerLoading);
        textView = itemView.findViewById(R.id.footerText);
    }

    /**
     * 根据 footer 状态切换转圈 / 文案 / 隐藏，错误态绑定重试回调。
     *
     * @param state         当前 footer 状态
     * @param retryListener 错误态点击重试回调（HIDDEN/LOADING/NO_MORE 时可为 null）
     */
    void bind(FooterState state, Runnable retryListener) {
        switch (state) {
            case LOADING:
                loadingView.setVisibility(View.VISIBLE);
                textView.setVisibility(View.GONE);
                itemView.setOnClickListener(null);
                break;
            case NO_MORE:
                loadingView.setVisibility(View.GONE);
                textView.setVisibility(View.VISIBLE);
                textView.setText(R.string.feed_no_more);
                itemView.setOnClickListener(null);
                break;
            case ERROR:
                loadingView.setVisibility(View.GONE);
                textView.setVisibility(View.VISIBLE);
                textView.setText(R.string.feed_error);
                // 错误态：点击 footer 触发重试。
                itemView.setOnClickListener(v -> {
                    if (retryListener != null) {
                        retryListener.run();
                    }
                });
                break;
            case HIDDEN:
            default:
                loadingView.setVisibility(View.GONE);
                textView.setVisibility(View.GONE);
                itemView.setOnClickListener(null);
                break;
        }
    }
}
