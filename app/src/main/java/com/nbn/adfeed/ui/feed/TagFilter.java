package com.nbn.adfeed.ui.feed;

import com.nbn.adfeed.data.model.AdItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 负责精确标签过滤
public class TagFilter {
    private TagFilter() {
    }
    //标签必须和items保持一致
    static List<AdItem> byTag(List<AdItem> items, String tag) {
        List<AdItem> result = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return result;
        }
        if (tag == null || tag.isEmpty()) {
            result.addAll(items);
            return result;
        }
        for (AdItem item : items) {
            if (item.getTags().contains(tag)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 按多个标签做 AND 过滤：只保留同时包含所有标签的广告。
     *
     * @param items 待过滤的广告列表
     * @param tags  筛选标签集合；为空时返回全部
     */
    static List<AdItem> byTags(List<AdItem> items, java.util.Collection<String> tags) {
        List<AdItem> result = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return result;
        }
        if (tags == null || tags.isEmpty()) {
            result.addAll(items);
            return result;
        }
        for (AdItem item : items) {
            // 命中条件：广告标签包含全部筛选标签（AND）。
            if (item.getTags().containsAll(tags)) {
                result.add(item);
            }
        }
        return result;
    }

    //按广告 ID 列表过滤广告列表
    static List<AdItem> byAdIds(List<AdItem> items, List<String> adIds) {
        List<AdItem> result = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return result;
        }
        if (adIds == null || adIds.isEmpty()) {
            result.addAll(items);
            return result;
        }

        Set<String> adIdSet = new HashSet<>(adIds);
        for (AdItem item : items) {
            if (adIdSet.contains(item.getId())) {
                result.add(item);
            }
        }
        return result;
    }
}
