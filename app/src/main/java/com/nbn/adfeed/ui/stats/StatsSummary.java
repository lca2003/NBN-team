package com.nbn.adfeed.ui.stats;

import com.nbn.adfeed.data.model.AdContentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class StatsSummary {
    private final int totalExposureCount;
    private final int totalClickCount;
    private final int totalAdCount;
    private final Map<AdContentType, Integer> contentTypeCounts;
    private final List<TopAd> topAds;
    private final List<TagHeat> tagHeat;

    StatsSummary(
            int totalExposureCount,
            int totalClickCount,
            int totalAdCount,
            Map<AdContentType, Integer> contentTypeCounts,
            List<TopAd> topAds,
            List<TagHeat> tagHeat
    ) {
        this.totalExposureCount = totalExposureCount;
        this.totalClickCount = totalClickCount;
        this.totalAdCount = totalAdCount;
        this.contentTypeCounts = Collections.unmodifiableMap(new EnumMap<>(contentTypeCounts));
        this.topAds = Collections.unmodifiableList(new ArrayList<>(topAds));
        this.tagHeat = Collections.unmodifiableList(new ArrayList<>(tagHeat));
    }

    public int getTotalExposureCount() {
        return totalExposureCount;
    }

    public int getTotalClickCount() {
        return totalClickCount;
    }

    public int getTotalAdCount() {
        return totalAdCount;
    }

    public Map<AdContentType, Integer> getContentTypeCounts() {
        return contentTypeCounts;
    }

    public int getContentTypeCount(AdContentType type) {
        Integer count = contentTypeCounts.get(type);
        return count == null ? 0 : count;
    }

    public List<TopAd> getTopAds() {
        return topAds;
    }

    public List<TagHeat> getTagHeat() {
        return tagHeat;
    }

    public int getClickRatePercent() {
        if (totalExposureCount == 0) {
            return 0;
        }
        return Math.round(totalClickCount * 100f / totalExposureCount);
    }

    public int getContentTypePercent(AdContentType type) {
        if (totalAdCount == 0) {
            return 0;
        }
        return Math.round(getContentTypeCount(type) * 100f / totalAdCount);
    }

    public static final class TopAd {
        private final String adId;
        private final String title;
        private final int exposureCount;
        private final int clickCount;

        TopAd(String adId, String title, int exposureCount, int clickCount) {
            this.adId = adId;
            this.title = title;
            this.exposureCount = exposureCount;
            this.clickCount = clickCount;
        }

        public String getAdId() {
            return adId;
        }

        public String getTitle() {
            return title;
        }

        public int getExposureCount() {
            return exposureCount;
        }

        public int getClickCount() {
            return clickCount;
        }
    }

    public static final class TagHeat {
        private final String name;
        private final int count;

        TagHeat(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }
    }
}
