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

    /** 标签背景色数组（循环使用，每个标签颜色不同）。 */
    private static final int[] TAG_BG_COLORS = {
            R.color.nbn_tag_color_1,
            R.color.nbn_tag_color_2,
            R.color.nbn_tag_color_3,
            R.color.nbn_tag_color_4
    };
    /** 标签文字色数组，与背景色一一对应。 */
    private static final int[] TAG_TEXT_COLORS = {
            R.color.nbn_tag_text_1,
            R.color.nbn_tag_text_2,
            R.color.nbn_tag_text_3,
            R.color.nbn_tag_text_4
    };

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
            container.addView(createChip(context, tags.get(i), i));
        }
    }

    /** 创建单个标签 chip，根据 index 循环选取不同颜色。胶囊形状，可点击。 */
    private static TextView createChip(Context context, String tag, int index) {
        TextView chip = new TextView(context);
        chip.setText(tag);
        chip.setTextSize(11f);
        // 每个标签颜色不同，循环使用 4 种配色。
        int colorIndex = index % TAG_BG_COLORS.length;
        chip.setTextColor(context.getColor(TAG_TEXT_COLORS[colorIndex]));
        // 动态设置背景色（用 GradientDrawable 实现胶囊圆角 + 不同底色）。
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(context.getColor(TAG_BG_COLORS[colorIndex]));
        bg.setCornerRadius(dp(context, 16));
        chip.setBackground(bg);
        chip.setGravity(Gravity.CENTER);
        // 可点击（后续可接入标签过滤搜索）。
        chip.setClickable(true);
        chip.setFocusable(true);
        // 内边距用像素，按屏幕密度换算，保证不同分辨率下视觉一致。
        int padH = dp(context, 10);
        int padV = dp(context, 4);
        chip.setPadding(padH, padV, padH, padV);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(context, 8);
        chip.setLayoutParams(params);
        return chip;
    }

    /** dp 转 px。 */
    private static int dp(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}