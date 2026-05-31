package com.nbn.adfeed.data.remote;

import com.nbn.adfeed.data.model.stitch.StitchConfigModels;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BackendPlatformDataSource {
    public interface Transport {
        String get(String path) throws RemoteAdException;

        String post(String path, String body) throws RemoteAdException;
    }

    public static final class BatchResult {
        public final int acceptedCount;
        public final int totalEventCount;
        public final int totalExposureCount;
        public final int validExposureCount;

        BatchResult(JSONObject data) {
            this.acceptedCount = Math.max(0, data.optInt("acceptedCount", 0));
            this.totalEventCount = Math.max(0, data.optInt("totalEventCount", 0));
            this.totalExposureCount = Math.max(0, data.optInt("totalExposureCount", 0));
            this.validExposureCount = Math.max(0, data.optInt("validExposureCount", 0));
        }
    }

    public static final class AnalyticsSummary {
        public final List<String> eventTypes;
        public final int minVisiblePercent;
        public final int minDwellMillis;
        public final boolean dedupeByAdAndPosition;
        public final int totalEventCount;
        public final int totalExposureCount;
        public final int validExposureCount;

        AnalyticsSummary(JSONObject data) {
            JSONObject summary = data.optJSONObject("analyticsSummary");
            JSONObject source = summary == null ? new JSONObject() : summary;
            JSONObject exposureRule = source.optJSONObject("exposureRule");
            JSONObject rule = exposureRule == null ? new JSONObject() : exposureRule;
            this.eventTypes = BackendJson.strings(source.optJSONArray("eventTypes"));
            this.minVisiblePercent = Math.max(0, rule.optInt("minVisiblePercent", 0));
            this.minDwellMillis = Math.max(0, rule.optInt("minDwellMillis", 0));
            this.dedupeByAdAndPosition = rule.optBoolean("dedupeByAdAndPosition", false);
            this.totalEventCount = Math.max(0, data.optInt("totalEventCount", 0));
            this.totalExposureCount = Math.max(0, data.optInt("totalExposureCount", 0));
            this.validExposureCount = Math.max(0, data.optInt("validExposureCount", 0));
        }
    }

    private final Transport transport;

    public BackendPlatformDataSource(Transport transport) {
        this.transport = transport == null ? defaultTransport() : transport;
    }

    public static BackendPlatformDataSource defaultDataSource() {
        return new BackendPlatformDataSource(defaultTransport());
    }

    public StitchConfigModels.AppRemoteConfig appConfig() throws RemoteAdException {
        JSONObject data = BackendJson.dataFromEnvelope(transport.get("/v1/config/app"), "platform");
        return parseRemoteConfig(BackendJson.requiredObject(data, "remoteConfig", "platform"));
    }

    public StitchConfigModels.AssetManifest assetManifest() throws RemoteAdException {
        JSONObject data = BackendJson.dataFromEnvelope(transport.get("/v1/assets/manifest"), "platform");
        return parseAssetManifest(data.optJSONArray("assetManifest"));
    }

    public JSONObject designContentHome() throws RemoteAdException {
        return BackendJson.dataFromEnvelope(transport.get("/v1/design-content/home"), "platform");
    }

    public BatchResult recordEvents(List<StitchConfigModels.StitchAnalyticsEvent> events)
            throws RemoteAdException {
        JSONArray eventArray = new JSONArray();
        if (events != null) {
            for (StitchConfigModels.StitchAnalyticsEvent event : events) {
                eventArray.put(BackendJson.object(
                        "eventId", event.eventId,
                        "eventType", event.eventType,
                        "adId", event.adId,
                        "sourcePage", event.sourcePage,
                        "timestampMillis", event.timestampMillis
                ));
            }
        }
        JSONObject body = BackendJson.object("events", eventArray);
        JSONObject data = BackendJson.dataFromEnvelope(
                transport.post("/v1/events/batch", body.toString()),
                "platform"
        );
        return new BatchResult(data);
    }

    public BatchResult recordExposures(List<StitchConfigModels.ExposureEvent> exposures)
            throws RemoteAdException {
        JSONArray exposureArray = new JSONArray();
        if (exposures != null) {
            for (StitchConfigModels.ExposureEvent exposure : exposures) {
                exposureArray.put(BackendJson.object(
                        "adId", exposure.adId,
                        "position", exposure.position,
                        "dwellTimeMillis", exposure.dwellTimeMillis,
                        "validExposure", exposure.validExposure
                ));
            }
        }
        JSONObject body = BackendJson.object("exposures", exposureArray);
        JSONObject data = BackendJson.dataFromEnvelope(
                transport.post("/v1/exposures/batch", body.toString()),
                "platform"
        );
        return new BatchResult(data);
    }

    public AnalyticsSummary analyticsSummary() throws RemoteAdException {
        JSONObject data = BackendJson.dataFromEnvelope(transport.get("/v1/analytics/summary"), "platform");
        return new AnalyticsSummary(data);
    }

    private static StitchConfigModels.AppRemoteConfig parseRemoteConfig(JSONObject config) {
        return new StitchConfigModels.AppRemoteConfig(
                parseFeatureSwitches(config.optJSONArray("featureSwitches")),
                parseChannels(config.optJSONArray("channels")),
                config.optBoolean("aiEnabled", false),
                config.optInt("requestTimeoutMillis", 1)
        );
    }

    private static List<StitchConfigModels.FeatureSwitch> parseFeatureSwitches(JSONArray switchesJson) {
        if (switchesJson == null) {
            return Collections.emptyList();
        }
        List<StitchConfigModels.FeatureSwitch> switches = new ArrayList<>();
        for (int index = 0; index < switchesJson.length(); index++) {
            JSONObject featureSwitch = switchesJson.optJSONObject(index);
            if (featureSwitch != null) {
                switches.add(new StitchConfigModels.FeatureSwitch(
                        featureSwitch.optString("key"),
                        featureSwitch.optBoolean("enabled", false),
                        featureSwitch.optString("description")
                ));
            }
        }
        return switches;
    }

    private static List<StitchConfigModels.ChannelConfig> parseChannels(JSONArray channelsJson) {
        if (channelsJson == null) {
            return Collections.emptyList();
        }
        List<StitchConfigModels.ChannelConfig> channels = new ArrayList<>();
        for (int index = 0; index < channelsJson.length(); index++) {
            JSONObject channel = channelsJson.optJSONObject(index);
            if (channel != null) {
                channels.add(new StitchConfigModels.ChannelConfig(
                        channel.optString("channelId"),
                        channel.optString("title"),
                        channel.optBoolean("visible", false),
                        channel.optInt("sortIndex", 0)
                ));
            }
        }
        return channels;
    }

    private static StitchConfigModels.AssetManifest parseAssetManifest(JSONArray assetsJson) {
        if (assetsJson == null) {
            return new StitchConfigModels.AssetManifest(Collections.emptyList());
        }
        List<StitchConfigModels.AssetEntry> entries = new ArrayList<>();
        for (int index = 0; index < assetsJson.length(); index++) {
            JSONObject asset = assetsJson.optJSONObject(index);
            if (asset != null) {
                entries.add(new StitchConfigModels.AssetEntry(
                        asset.optString("assetId"),
                        asset.optString("localAssetName"),
                        asset.optString("remoteUrl"),
                        asset.optString("assetType"),
                        asset.optInt("width", 0),
                        asset.optInt("height", 0)
                ));
            }
        }
        return new StitchConfigModels.AssetManifest(entries);
    }

    private static Transport defaultTransport() {
        return new Transport() {
            @Override
            public String get(String path) throws RemoteAdException {
                return request("GET", path, "");
            }

            @Override
            public String post(String path, String body) throws RemoteAdException {
                return request("POST", path, body);
            }

            private String request(String method, String path, String body) throws RemoteAdException {
                RemoteAdException lastException = null;
                for (BackendConfig candidate : BackendConfig.defaultCandidates()) {
                    try {
                        HttpApiClient client = new HttpApiClient(candidate);
                        return "POST".equals(method) ? client.post(path, body) : client.get(path);
                    } catch (RemoteAdException exception) {
                        lastException = exception;
                    }
                }
                throw lastException == null
                        ? new RemoteAdException(RemoteAdException.Reason.NETWORK, "Backend platform API unavailable")
                        : lastException;
            }
        };
    }
}
