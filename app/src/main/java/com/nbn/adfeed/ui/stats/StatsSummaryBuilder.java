package com.nbn.adfeed.ui.stats;

import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionState;
import com.nbn.adfeed.ui.feed.InteractionStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StatsSummaryBuilder {
    private static final int TOP_AD_LIMIT = 5;
    private static final int TAG_HEAT_LIMIT = 6;

    private StatsSummaryBuilder() {
    }

    // Inputs come from AdRepository and InteractionStore; project models treat them as non-null.
    public static StatsSummary fromAds(List<AdItem> ads, InteractionStore interactionStore) {
        Map<AdContentType, Integer> contentTypeCounts = new EnumMap<>(AdContentType.class);
        for (AdContentType type : AdContentType.values()) {
            contentTypeCounts.put(type, 0);
        }

        int totalExposureCount = 0;
        int totalClickCount = 0;
        List<StatsSummary.TopAd> topAds = new ArrayList<>();
        Map<String, Integer> tagCounts = new HashMap<>();

        for (AdItem ad : ads) {
            InteractionState state = interactionStore.stateOf(ad);
            int exposureCount = state.getExposureCount();
            int clickCount = state.getClickCount();

            totalExposureCount += exposureCount;
            totalClickCount += clickCount;
            contentTypeCounts.put(ad.getContentType(), contentTypeCounts.get(ad.getContentType()) + 1);
            topAds.add(new StatsSummary.TopAd(ad.getId(), ad.getTitle(), exposureCount, clickCount));

            for (String tag : ad.getTags()) {
                tagCounts.put(tag, tagCounts.getOrDefault(tag, 0) + 1);
            }
        }

        topAds.sort(Comparator
                .comparingInt(StatsSummary.TopAd::getExposureCount).reversed()
                .thenComparing(Comparator.comparingInt(StatsSummary.TopAd::getClickCount).reversed())
                .thenComparing(StatsSummary.TopAd::getAdId));

        List<StatsSummary.TagHeat> tagHeat = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : tagCounts.entrySet()) {
            tagHeat.add(new StatsSummary.TagHeat(entry.getKey(), entry.getValue()));
        }
        tagHeat.sort(Comparator
                .comparingInt(StatsSummary.TagHeat::getCount).reversed()
                .thenComparing(StatsSummary.TagHeat::getName));

        return new StatsSummary(
                totalExposureCount,
                totalClickCount,
                ads.size(),
                contentTypeCounts,
                limit(topAds, TOP_AD_LIMIT),
                limit(tagHeat, TAG_HEAT_LIMIT)
        );
    }

    private static <T> List<T> limit(List<T> items, int limit) {
        if (items.size() <= limit) {
            return items;
        }
        return new ArrayList<>(items.subList(0, limit));
    }
}
