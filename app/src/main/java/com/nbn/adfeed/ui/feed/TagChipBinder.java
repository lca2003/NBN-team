package com.nbn.adfeed.ui.feed;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nbn.adfeed.R;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 标签 chip 工具类：把广告的 tags 动态渲染成一排圆角小标签填进容器。
 *
 * <p>因为当前主题是 {@code Theme.AppCompat}，不便引入 Material 的 Chip 控件，这里用
 * 轻量的 {@link TextView} + 圆角背景自绘 chip，零主题依赖。标签数量遵循接口约定的 3-5 个。
 * 信息流卡片与详情页共用本工具，视觉保持一致。</p>
 *
 * <p>支持"选中态"：当某个标签处于当前筛选集合中时，渲染为更醒目的实心高亮样式（描边+加粗）。
 * 另外提供 {@link #createFilterChip} 给顶部筛选栏渲染"可删除"的已选标签（带 ✕）。</p>
 */
public final class TagChipBinder {

    /** 最多展示的标签数量，避免一行塞太多影响卡片高度。 */
    private static final int MAX_TAGS = 4;

    private TagChipBinder() {
    }

    // 外部可以传入一个监听器，当某个标签被点击时，把这个标签文本回传出去
    public interface OnTagClickListener {
        void onTagClick(String tag);
    }

    /** 筛选栏 chip 的删除回调。 */
    public interface OnTagRemoveListener {
        void onTagRemove(String tag);
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
        bind(container, tags, null, null);
    }

    //重载bind创建每个 chip 时把 listener 传进去
    public static void bind(LinearLayout container, List<String> tags, OnTagClickListener listener) {
        bind(container, tags, listener, null);
    }

    /**
     * 完整版 bind：渲染标签，并把处于 {@code selectedTags} 中的标签渲染成醒目的选中态。
     *
     * <p>复用已有子 View 而非每次销毁重建，减少滚动时 View 分配与 GC 压力。</p>
     *
     * @param selectedTags 当前筛选选中的标签集合；命中的标签会高亮。可为 null。
     */
    public static void bind(LinearLayout container, List<String> tags,
                            OnTagClickListener listener, Collection<String> selectedTags) {
        if (tags == null || tags.isEmpty()) {
            container.removeAllViews();
            container.setVisibility(ViewGroup.GONE);
            return;
        }
        container.setVisibility(ViewGroup.VISIBLE);

        Collection<String> selected = selectedTags == null ? Collections.emptySet() : selectedTags;
        Context context = container.getContext();
        int count = Math.min(tags.size(), MAX_TAGS);

        // 优先复用已有 child，只在数量不足时新建。
        for (int i = 0; i < count; i++) {
            String tag = tags.get(i);
            boolean isSelected = selected.contains(tag);
            if (i < container.getChildCount()) {
                // 复用已有 TextView，原地更新属性。
                updateChip((TextView) container.getChildAt(i), context, tag, i, listener, isSelected);
            } else {
                container.addView(createChip(context, tag, i, listener, isSelected));
            }
        }
        // 去除多余的旧 child（如上次显示 4 个标签，这次只显示 2 个）。
        while (container.getChildCount() > count) {
            container.removeViewAt(container.getChildCount() - 1);
        }
    }

    /** 原地更新一个已存在的 chip，零分配。 */
    private static void updateChip(TextView chip, Context context, String tag, int index,
                                   OnTagClickListener listener, boolean selected) {
        int colorIndex = index % TAG_BG_COLORS.length;
        int bgColor = context.getColor(TAG_BG_COLORS[colorIndex]);
        int textColor = context.getColor(TAG_TEXT_COLORS[colorIndex]);

        chip.setText(tag);

        GradientDrawable bg = (GradientDrawable) chip.getBackground();
        if (bg == null) {
            bg = new GradientDrawable();
            bg.setCornerRadius(dp(context, 20));
            chip.setBackground(bg);
        }
        if (selected) {
            bg.setColor(textColor);
            bg.setStroke(dp(context, 2), textColor);
            chip.setTextColor(0xFFFFFFFF);
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        } else {
            bg.setColor(bgColor);
            bg.setStroke(0, 0);
            chip.setTextColor(textColor);
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.NORMAL);
        }

        chip.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTagClick(tag);
            }
        });
    }

    /** 创建单个标签 chip，根据 index 循环选取不同颜色。胶囊形状，可点击。 */
    private static TextView createChip(Context context,
                                       String tag,
                                       int index,
                                       OnTagClickListener listener,
                                       boolean selected) {
        TextView chip = new TextView(context);
        chip.setText(tag);
        chip.setTextSize(13f);
        int colorIndex = index % TAG_BG_COLORS.length;
        int bgColor = context.getColor(TAG_BG_COLORS[colorIndex]);
        int textColor = context.getColor(TAG_TEXT_COLORS[colorIndex]);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(context, 20));
        if (selected) {
            // 选中态：实心高亮（用标签主色做底）+ 白字 + 加粗 + 描边，做出醒目对比。
            bg.setColor(textColor);
            bg.setStroke(dp(context, 2), textColor);
            chip.setTextColor(0xFFFFFFFF);
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        } else {
            // 普通态：浅色底 + 同色系文字。
            bg.setColor(bgColor);
            chip.setTextColor(textColor);
        }
        chip.setBackground(bg);
        chip.setGravity(Gravity.CENTER);

        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTagClick(tag);
            }
        });

        int padH = dp(context, 14);
        int padV = dp(context, 7);
        chip.setPadding(padH, padV, padH, padV);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(context, 10);
        chip.setLayoutParams(params);
        return chip;
    }

    /**
     * 创建顶部筛选栏用的"已选标签"chip：文字后带 ✕，整体点击即取消该筛选。
     *
     * @param tag      标签文字
     * @param index    用于循环取色，保持与卡片标签同色系
     * @param onRemove 点击 ✕ / chip 时的取消回调
     */
    public static TextView createFilterChip(Context context, String tag, int index,
                                            OnTagRemoveListener onRemove) {
        int colorIndex = index % TAG_BG_COLORS.length;
        int textColor = context.getColor(TAG_TEXT_COLORS[colorIndex]);

        TextView chip = new TextView(context);
        // 文字后跟一个细分隔与 ✕，整体可点击取消。
        chip.setText(tag + "  ✕");
        chip.setTextSize(13f);
        chip.setTextColor(0xFFFFFFFF);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(context, 20));
        bg.setColor(textColor); // 实心高亮，表示"正在筛选"。
        chip.setBackground(bg);
        chip.setGravity(Gravity.CENTER);

        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> {
            if (onRemove != null) {
                onRemove.onTagRemove(tag);
            }
        });

        int padH = dp(context, 12);
        int padV = dp(context, 6);
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
