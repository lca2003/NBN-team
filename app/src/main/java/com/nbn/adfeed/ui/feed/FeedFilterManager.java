package com.nbn.adfeed.ui.feed;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 标签筛选管理器，从 FeedFragment 中提取。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>管理多标签 AND 筛选状态（{@code currentTagFilters}）；</li>
 *   <li>管理搜索结果 ID 过滤（{@code currentSearchAdIds}）；</li>
 *   <li>渲染顶部筛选栏 UI（已选标签 chip + 清除全部按钮）；</li>
 *   <li>同步 adapter 的标签选中态，使卡片上命中标签高亮。</li>
 * </ul>
 *
 * <p>筛选变化时通过 {@code onFilterChanged} 回调通知 Fragment 触发数据刷新。</p>
 */
final class FeedFilterManager {

    /** 多标签 AND 筛选集合，用 LinkedHashSet 保留点击顺序。 */
    private final LinkedHashSet<String> currentTagFilters = new LinkedHashSet<>();
    /** 搜索结果过滤：仅展示匹配的广告 ID。null 表示无搜索过滤。 */
    private List<String> currentSearchAdIds = null;

    private Context context;
    private FeedAdapter adapter;
    private View filterBarContainer;
    private LinearLayout filterBar;

    /** 筛选条件变化时回调，由 Fragment 注入以触发数据刷新。 */
    private Runnable onFilterChanged;

    /**
     * 绑定外部依赖。在 Fragment.onViewCreated 之后调用。
     */
    void bind(Context context, FeedAdapter adapter,
              View filterBarContainer, LinearLayout filterBar) {
        this.context = context;
        this.adapter = adapter;
        this.filterBarContainer = filterBarContainer;
        this.filterBar = filterBar;
    }

    void setOnFilterChangedListener(Runnable listener) {
        this.onFilterChanged = listener;
    }

    // ---- 标签筛选操作 ----

    /**
     * 切换标签筛选：再次点击同一标签则取消，否则加入筛选集合。
     * 触发时会清除搜索结果过滤并回调 onFilterChanged。
     */
    void toggleTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        currentSearchAdIds = null;
        if (currentTagFilters.contains(tag)) {
            currentTagFilters.remove(tag);
        } else {
            currentTagFilters.add(tag);
        }
        renderFilterBar();
        if (onFilterChanged != null) {
            onFilterChanged.run();
        }
    }

    /** 取消单个标签筛选（点击 chip 上的 ✕ 时触发）。 */
    void removeTag(String tag) {
        if (!currentTagFilters.remove(tag)) {
            return;
        }
        renderFilterBar();
        if (onFilterChanged != null) {
            onFilterChanged.run();
        }
    }

    /** 清除全部标签筛选并回调（供"清除全部"按钮使用）。 */
    void clearAll() {
        if (currentTagFilters.isEmpty()) {
            return;
        }
        currentTagFilters.clear();
        renderFilterBar();
        if (onFilterChanged != null) {
            onFilterChanged.run();
        }
    }

    /**
     * 静默重置所有筛选状态（清标签 + 清搜索 ID），不触发回调。
     * 供 {@link FeedDataController} 频道切换时使用，由调用方统一触发刷新。
     */
    void resetAll() {
        currentTagFilters.clear();
        currentSearchAdIds = null;
        renderFilterBar();
    }

    // ---- 搜索结果 ----

    /**
     * 应用搜索结果，替换当前标签筛选。
     * 调用后需由外部触发数据刷新。
     */
    void applySearchResult(List<String> matchedAdIds) {
        currentTagFilters.clear();
        renderFilterBar();
        currentSearchAdIds = (matchedAdIds == null || matchedAdIds.isEmpty())
                ? null
                : new ArrayList<>(matchedAdIds);
    }

    // ---- 查询 ----

    /** 获取当前标签筛选列表的快照（供数据加载使用）。 */
    List<String> getTagFilters() {
        return new ArrayList<>(currentTagFilters);
    }

    /** 获取当前搜索 ID 列表（供数据加载使用）。null 表示无搜索过滤。 */
    List<String> getSearchAdIds() {
        return currentSearchAdIds;
    }

    /** 是否有激活的筛选条件。 */
    boolean hasActiveFilters() {
        return !currentTagFilters.isEmpty() || currentSearchAdIds != null;
    }

    // ---- 内部渲染 ----

    /**
     * 渲染筛选栏：把当前选中的标签以可取消的 chip 形式展示在频道按钮下方。
     * 没有选中标签时整栏隐藏。同时同步给 adapter，让卡片上的同名标签显示选中态。
     */
    private void renderFilterBar() {
        if (adapter != null) {
            adapter.setSelectedTags(currentTagFilters);
        }
        if (filterBarContainer == null || filterBar == null) {
            return;
        }
        filterBar.removeAllViews();
        if (currentTagFilters.isEmpty()) {
            filterBarContainer.setVisibility(View.GONE);
            return;
        }
        filterBarContainer.setVisibility(View.VISIBLE);
        int index = 0;
        for (String tag : currentTagFilters) {
            filterBar.addView(TagChipBinder.createFilterChip(context, tag, index, this::removeTag));
            index++;
        }
    }
}
