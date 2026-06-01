package com.nbn.adfeed.ui.feed;

import com.nbn.adfeed.data.model.AdItem;

import java.util.ArrayList;
import java.util.List;

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
}
