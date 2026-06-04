package com.nbn.adfeed.ai.demo;

import com.nbn.adfeed.ai.AiGenerationException;
import com.nbn.adfeed.ai.tagging.TagGenerator;
import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DemoRemoteTagGenerator implements TagGenerator {
    private static final int MAX_TAG_COUNT = 5;
    private static final int MAX_TAG_LENGTH = 6;

    @Override
    public List<String> generateTags(AdItem item) throws AiGenerationException {
        if (isWeakContent(item)) {
            throw new AiGenerationException("weak ad content for demo remote tags");
        }

        Set<String> tags = new LinkedHashSet<>();
        addTag(tags, item.getChannel());
        addTag(tags, contentTypeTag(item.getContentType()));
        addTag(tags, inferScene(item));
        addTag(tags, firstItemTag(item));
        addTag(tags, "精选");
        return new ArrayList<>(tags);
    }

    private static boolean isWeakContent(AdItem item) {
        return isBlank(item.getDescription()) && isBlank(item.getSummary());
    }

    private static String contentTypeTag(AdContentType contentType) {
        return contentType == AdContentType.VIDEO ? "视频" : "图文";
    }

    private static String inferScene(AdItem item) {
        String text = safe(item.getTitle()) + safe(item.getDescription()) + safe(item.getSummary());
        if (text.contains("学生") || text.contains("校园") || text.contains("宿舍")) {
            return "学生党";
        }
        if (text.contains("通勤") || text.contains("上班")) {
            return "通勤";
        }
        if (text.contains("咖啡")) {
            return "咖啡";
        }
        if (text.contains("运动") || text.contains("健身") || text.contains("跑")) {
            return "运动";
        }
        return "生活";
    }

    private static String firstItemTag(AdItem item) {
        for (String tag : item.getTags()) {
            if (!isBlank(tag)) {
                return tag;
            }
        }
        return item.getBrand();
    }

    private static void addTag(Set<String> target, String tag) {
        if (target.size() >= MAX_TAG_COUNT || isBlank(tag)) {
            return;
        }
        String normalized = tag.trim();
        if (normalized.length() > MAX_TAG_LENGTH) {
            normalized = normalized.substring(0, MAX_TAG_LENGTH);
        }
        target.add(normalized);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
