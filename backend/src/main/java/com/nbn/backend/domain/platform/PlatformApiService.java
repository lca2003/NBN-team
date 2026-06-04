package com.nbn.backend.domain.platform;

import com.nbn.backend.store.JsonSeedStore;

import org.json.JSONArray;
import org.json.JSONObject;

public final class PlatformApiService {
    private final JsonSeedStore seedStore;
    private final JSONObject remoteConfig;
    private final JSONArray assetManifest;
    private final JSONObject analyticsSummarySeed;
    private final JSONArray events = new JSONArray();
    private final JSONArray exposures = new JSONArray();

    public PlatformApiService(JsonSeedStore seedStore) {
        this.seedStore = seedStore;
        JSONObject appConfig = seedStore.documentCopy("app_config.json");
        this.remoteConfig = appConfig.getJSONObject("remoteConfig");
        this.assetManifest = appConfig.getJSONArray("assetManifest");
        this.analyticsSummarySeed = appConfig.getJSONObject("analyticsSummary");
        JSONArray persistedEvents = appConfig.optJSONArray("events");
        if (persistedEvents != null) {
            copyInto(persistedEvents, events);
        }
        JSONArray persistedExposures = appConfig.optJSONArray("exposures");
        if (persistedExposures != null) {
            copyInto(persistedExposures, exposures);
        }
    }

    public synchronized String appConfigJson() {
        return "{\"remoteConfig\":" + copy(remoteConfig) + "}";
    }

    public synchronized String assetManifestJson() {
        return "{\"assetManifest\":" + new JSONArray(assetManifest.toString()) + "}";
    }

    public synchronized String designContentHomeJson() {
        return new JSONObject()
                .put("homeFeed", seedStore.documentCopy("home_feed.json"))
                .put("remoteConfig", copy(remoteConfig))
                .toString();
    }

    public synchronized String recordEvents(String requestBody) {
        JSONArray incoming = arrayFromBody(requestBody, "events");
        for (int index = 0; index < incoming.length(); index++) {
            events.put(copy(incoming.getJSONObject(index))
                    .put("serverReceivedAtMillis", System.currentTimeMillis()));
        }
        persistAppConfig();
        return new JSONObject()
                .put("acceptedCount", incoming.length())
                .put("totalEventCount", events.length())
                .toString();
    }

    public synchronized String recordExposures(String requestBody) {
        JSONArray incoming = arrayFromBody(requestBody, "exposures");
        for (int index = 0; index < incoming.length(); index++) {
            exposures.put(copy(incoming.getJSONObject(index))
                    .put("serverReceivedAtMillis", System.currentTimeMillis()));
        }
        persistAppConfig();
        return new JSONObject()
                .put("acceptedCount", incoming.length())
                .put("totalExposureCount", exposures.length())
                .put("validExposureCount", validExposureCount())
                .toString();
    }

    public synchronized String analyticsSummaryJson() {
        return new JSONObject()
                .put("analyticsSummary", copy(analyticsSummarySeed))
                .put("totalEventCount", events.length())
                .put("totalExposureCount", exposures.length())
                .put("validExposureCount", validExposureCount())
                .toString();
    }

    private void persistAppConfig() {
        seedStore.writeState("app_config.json", new JSONObject()
                .put("remoteConfig", copy(remoteConfig))
                .put("assetManifest", new JSONArray(assetManifest.toString()))
                .put("analyticsSummary", copy(analyticsSummarySeed))
                .put("events", new JSONArray(events.toString()))
                .put("exposures", new JSONArray(exposures.toString())));
    }

    private static void copyInto(JSONArray source, JSONArray target) {
        for (int index = 0; index < source.length(); index++) {
            target.put(copy(source.getJSONObject(index)));
        }
    }

    private int validExposureCount() {
        int count = 0;
        for (int index = 0; index < exposures.length(); index++) {
            JSONObject exposure = exposures.getJSONObject(index);
            boolean explicitlyValid = exposure.optBoolean("validExposure", false);
            int visiblePercent = exposure.optInt("visiblePercent", 0);
            int dwellMillis = exposure.optInt("dwellMillis", exposure.optInt("dwellTimeMillis", 0));
            if (explicitlyValid || visiblePercent >= 50 && dwellMillis >= 1000) {
                count++;
            }
        }
        return count;
    }

    private static JSONArray arrayFromBody(String requestBody, String preferredKey) {
        String normalized = requestBody == null ? "" : requestBody.trim();
        if (normalized.isEmpty()) {
            return new JSONArray();
        }
        JSONObject body = new JSONObject(normalized);
        JSONArray items = body.optJSONArray(preferredKey);
        if (items == null) {
            items = body.optJSONArray("items");
        }
        return items == null ? new JSONArray() : items;
    }

    private static JSONObject copy(JSONObject source) {
        return new JSONObject(source.toString());
    }
}
