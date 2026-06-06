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
    private final int totalLikeCount;
    private final int totalCollectCount;
    private final int totalShareCount;
    private final int totalAdCount;
    private final Map<AdContentType, Integer> contentTypeCounts;
    private final List<TopAd> topAds;
    private final List<TagHeat> tagHeat;

    StatsSummary(
            int totalExposureCount,
            int totalClickCount,
            int totalLikeCount,
            int totalCollectCount,
            int totalShareCount,
            int totalAdCount,
            Map<AdContentType, Integer> contentTypeCounts,
            List<TopAd> topAds,
            List<TagHeat> tagHeat
    ) {
        this.totalExposureCount = totalExposureCount;
        this.totalClickCount = totalClickCount;
        this.totalLikeCount = totalLikeCount;
        this.totalCollectCount = totalCollectCount;
        this.totalShareCount = totalShareCount;
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

    public int getTotalLikeCount() {
        return totalLikeCount;
    }

    public int getTotalCollectCount() {
        return totalCollectCount;
    }

    public int getTotalShareCount() {
        return totalShareCount;
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

    public int getInteractionRatePercent() {
        if (totalExposureCount == 0) {
            return 0;
        }
        return Math.round((totalLikeCount + totalCollectCount + totalShareCount) * 100f / totalExposureCount);
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
        private final int likeCount;
        private final int collectCount;
        private final int shareCount;

        TopAd(
                String adId,
                String title,
                int exposureCount,
                int clickCount,
                int likeCount,
                int collectCount,
                int shareCount
        ) {
            this.adId = adId;
            this.title = title;
            this.exposureCount = exposureCount;
            this.clickCount = clickCount;
            this.likeCount = likeCount;
            this.collectCount = collectCount;
            this.shareCount = shareCount;
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

        public int getLikeCount() {
            return likeCount;
        }

        public int getCollectCount() {
            return collectCount;
        }

        public int getShareCount() {
            return shareCount;
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
