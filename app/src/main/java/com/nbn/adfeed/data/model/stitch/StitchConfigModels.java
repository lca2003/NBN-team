package com.nbn.adfeed.data.model.stitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StitchConfigModels {
    private StitchConfigModels() {
    }

    public static final class AppRemoteConfig {
        public final List<FeatureSwitch> featureSwitches;
        public final List<ChannelConfig> channels;
        public final boolean aiEnabled;
        public final int requestTimeoutMillis;

        public AppRemoteConfig(List<FeatureSwitch> featureSwitches, List<ChannelConfig> channels,
                               boolean aiEnabled, int requestTimeoutMillis) {
            this.featureSwitches = immutable(featureSwitches);
            this.channels = immutable(channels);
            this.aiEnabled = aiEnabled;
            this.requestTimeoutMillis = Math.max(1, requestTimeoutMillis);
        }
    }

    public static final class FeatureSwitch {
        public final String key;
        public final boolean enabled;
        public final String description;

        public FeatureSwitch(String key, boolean enabled, String description) {
            this.key = safe(key);
            this.enabled = enabled;
            this.description = safe(description);
        }
    }

    public static final class ChannelConfig {
        public final String channelId;
        public final String title;
        public final boolean visible;
        public final int sortIndex;

        public ChannelConfig(String channelId, String title, boolean visible, int sortIndex) {
            this.channelId = safe(channelId);
            this.title = safe(title);
            this.visible = visible;
            this.sortIndex = Math.max(0, sortIndex);
        }
    }

    public static final class StitchAnalyticsEvent {
        public final String eventId;
        public final String eventType;
        public final String adId;
        public final String sourcePage;
        public final long timestampMillis;

        public StitchAnalyticsEvent(String eventId, String eventType, String adId,
                                    String sourcePage, long timestampMillis) {
            this.eventId = safe(eventId);
            this.eventType = safe(eventType);
            this.adId = safe(adId);
            this.sourcePage = safe(sourcePage);
            this.timestampMillis = Math.max(0L, timestampMillis);
        }
    }

    public static final class ExposureEvent {
        public final String adId;
        public final int position;
        public final long dwellTimeMillis;
        public final boolean validExposure;

        public ExposureEvent(String adId, int position, long dwellTimeMillis, boolean validExposure) {
            this.adId = safe(adId);
            this.position = Math.max(0, position);
            this.dwellTimeMillis = Math.max(0L, dwellTimeMillis);
            this.validExposure = validExposure;
        }
    }

    public static final class AssetManifest {
        public final List<AssetEntry> entries;

        public AssetManifest(List<AssetEntry> entries) {
            this.entries = immutable(entries);
        }
    }

    public static final class AssetEntry {
        public final String assetId;
        public final String localAssetName;
        public final String remoteUrl;
        public final String assetType;
        public final int width;
        public final int height;

        public AssetEntry(String assetId, String localAssetName, String remoteUrl,
                          String assetType, int width, int height) {
            this.assetId = safe(assetId);
            this.localAssetName = safe(localAssetName);
            this.remoteUrl = safe(remoteUrl);
            this.assetType = safe(assetType);
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null ? Collections.emptyList() : values));
    }
}
