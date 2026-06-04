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
