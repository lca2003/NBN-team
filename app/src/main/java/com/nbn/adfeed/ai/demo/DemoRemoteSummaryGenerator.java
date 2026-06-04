package com.nbn.adfeed.ai.demo;

import com.nbn.adfeed.ai.AiGenerationException;
import com.nbn.adfeed.ai.summary.SummaryGenerator;
import com.nbn.adfeed.data.model.AdItem;

public final class DemoRemoteSummaryGenerator implements SummaryGenerator {
    private static final int MAX_SUMMARY_LENGTH = 40;

    @Override
    public String generateSummary(AdItem item) throws AiGenerationException {
        if (isWeakContent(item)) {
            throw new AiGenerationException("weak ad content for demo remote summary");
        }

        String summary = firstNonBlank(item.getSummary(), item.getDescription());
        String prefix = firstNonBlank(item.getBrand(), item.getChannel());
        return limit(prefix + "：" + summary);
    }

    private static boolean isWeakContent(AdItem item) {
        return isBlank(item.getDescription()) && isBlank(item.getSummary());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String limit(String value) {
        String normalized = value == null ? "" : value.replace('\n', ' ').trim();
        if (normalized.length() <= MAX_SUMMARY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SUMMARY_LENGTH);
    }
}
