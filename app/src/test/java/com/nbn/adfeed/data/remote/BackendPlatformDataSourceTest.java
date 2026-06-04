package com.nbn.adfeed.data.remote;

import com.nbn.adfeed.data.model.stitch.StitchConfigModels;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class BackendPlatformDataSourceTest {
    @Test
    public void platformClientCallsConfigAssetsEventsExposuresAndAnalyticsRoutes() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        BackendPlatformDataSource dataSource = new BackendPlatformDataSource(transport);

        StitchConfigModels.AppRemoteConfig config = dataSource.appConfig();
        StitchConfigModels.AssetManifest manifest = dataSource.assetManifest();
        org.json.JSONObject designContent = dataSource.designContentHome();
        BackendPlatformDataSource.BatchResult events = dataSource.recordEvents(List.of(
                new StitchConfigModels.StitchAnalyticsEvent("event_001", "AD_CLICK", "ad_001", "feed", 100L)
        ));
        BackendPlatformDataSource.BatchResult exposures = dataSource.recordExposures(List.of(
                new StitchConfigModels.ExposureEvent("ad_001", 1, 1500L, true)
        ));
        BackendPlatformDataSource.AnalyticsSummary summary = dataSource.analyticsSummary();

        assertTrue(config.aiEnabled);
        assertEquals(1, manifest.entries.size());
        assertTrue(designContent.has("homeFeed"));
        assertEquals(1, events.acceptedCount);
        assertEquals(1, exposures.validExposureCount);
        assertEquals(2, summary.eventTypes.size());
        assertEquals(50, summary.minVisiblePercent);
        assertEquals("GET /v1/config/app", transport.calls.get(0));
        assertEquals("GET /v1/assets/manifest", transport.calls.get(1));
        assertEquals("GET /v1/design-content/home", transport.calls.get(2));
        assertEquals("POST /v1/events/batch", transport.calls.get(3));
        assertEquals("POST /v1/exposures/batch", transport.calls.get(4));
        assertEquals("GET /v1/analytics/summary", transport.calls.get(5));
        assertTrue(transport.requestBodies.get(3).contains("AD_CLICK"));
        assertTrue(transport.requestBodies.get(4).contains("validExposure"));
    }

    @Test(expected = RemoteAdException.class)
    public void nonOkEnvelopeThrowsRemoteException() throws Exception {
        BackendPlatformDataSource dataSource = new BackendPlatformDataSource(new RecordingTransport() {
            @Override
            public String get(String path) {
                return "{\"requestId\":\"req-test\",\"code\":\"REMOTE_ERROR\",\"message\":\"down\",\"data\":null}";
            }
        });

        dataSource.appConfig();
    }

    private static class RecordingTransport implements BackendPlatformDataSource.Transport {
        private final java.util.List<String> calls = new java.util.ArrayList<>();
        private final java.util.List<String> requestBodies = new java.util.ArrayList<>();

        @Override
        public String get(String path) {
            calls.add("GET " + path);
            requestBodies.add("");
            if ("/v1/config/app".equals(path)) {
                return ok("{\"remoteConfig\":" + configJson() + "}");
            }
            if ("/v1/assets/manifest".equals(path)) {
                return ok("{\"assetManifest\":[{\"assetId\":\"asset_001\","
                        + "\"localAssetName\":\"local.png\",\"remoteUrl\":\"https://example.test/a.png\","
                        + "\"assetType\":\"image\",\"width\":1200,\"height\":800}]}");
            }
            if ("/v1/design-content/home".equals(path)) {
                return ok("{\"homeFeed\":{\"items\":[]},\"remoteConfig\":" + configJson() + "}");
            }
            return ok("{\"analyticsSummary\":{\"eventTypes\":[\"AD_CLICK\",\"SEARCH\"],"
                    + "\"exposureRule\":{\"minVisiblePercent\":50,\"minDwellMillis\":1000,"
                    + "\"dedupeByAdAndPosition\":true}},\"totalEventCount\":1,"
                    + "\"totalExposureCount\":1,\"validExposureCount\":1}");
        }

        @Override
        public String post(String path, String body) {
            calls.add("POST " + path);
            requestBodies.add(body == null ? "" : body);
            if (path.contains("exposures")) {
                return ok("{\"acceptedCount\":1,\"totalExposureCount\":1,\"validExposureCount\":1}");
            }
            return ok("{\"acceptedCount\":1,\"totalEventCount\":1}");
        }

        private static String ok(String dataJson) {
            return "{\"requestId\":\"req-test\",\"code\":\"OK\",\"message\":\"ok\",\"data\":" + dataJson + "}";
        }

        private static String configJson() {
            return "{\"aiEnabled\":true,\"requestTimeoutMillis\":3000,"
                    + "\"featureSwitches\":[{\"key\":\"profile_edit_enabled\","
                    + "\"enabled\":true,\"description\":\"test\"}],"
                    + "\"channels\":[{\"channelId\":\"featured\",\"title\":\"精选\","
                    + "\"visible\":true,\"sortIndex\":0}]}";
        }
    }
}
