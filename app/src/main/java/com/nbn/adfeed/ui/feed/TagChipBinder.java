package com.nbn.adfeed.ui.feed;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nbn.adfeed.R;

import java.util.List;

/**
 * 标签 chip 工具类：把广告的 tags 动态渲染成一排圆角小标签填进容器。
 *
 * <p>因为当前主题是 {@code Theme.AppCompat}，不便引入 Material 的 Chip 控件，这里用
 * 轻量的 {@link TextView} + 圆角背景自绘 chip，零主题依赖。标签数量遵循接口约定的 3-5 个。
 * 信息流卡片与详情页共用本工具，视觉保持一致。</p>
 */
public final class TagChipBinder {

    /** 最多展示的标签数量，避免一行塞太多影响卡片高度。 */
    private static final int MAX_TAGS = 4;

    private TagChipBinder() {
    }

    /**
     * 用 tags 重建 container 的子 View。
     *
     * @param container 卡片里的标签容器（横向 LinearLayout）
     * @param tags      广告标签列表
     */
    public static void bind(LinearLayout container, List<String> tags) {
        container.removeAllViews();
        if (tags == null || tags.isEmpty()) {
            container.setVisibility(ViewGroup.GONE);
            return;
        }
        container.setVisibility(ViewGroup.VISIBLE);

        Context context = container.getContext();
        int count = Math.min(tags.size(), MAX_TAGS);
        for (int i = 0; i < count; i++) {
            container.addView(createChip(context, tags.get(i)));
        }
    }

    /** 创建单个标签 chip。 */
    private static TextView createChip(Context context, String tag) {
        TextView chip = new TextView(context);
        chip.setText(tag);
        chip.setTextSize(11f);
        chip.setTextColor(context.getColor(R.color.nbn_brand));
        chip.setGravity(Gravity.CENTER);
        chip.setBackgroundResource(R.drawable.bg_tag_chip);
        // 内边距用像素，按屏幕密度换算，保证不同分辨率下视觉一致。
        int padH = dp(context, 10);
        int padV = dp(context, 4);
        chip.setPadding(padH, padV, padH, padV);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(context, 6);
        chip.setLayoutParams(params);
        return chip;
    }

    /** dp 转 px。 */
    private static int dp(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}