package com.nbn.adfeed.data.remote;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BackendStitchDataSourceTest {
    @Test
    public void mapsStitchAssetUrlToBackendPageName() {
        assertEquals("home", BackendStitchDataSource.pageNameFromUrl("file:///android_asset/stitch_ui/home.html"));
        assertEquals("search", BackendStitchDataSource.pageNameFromUrl("file:///android_asset/stitch_ui/search.html"));
        assertEquals("detail", BackendStitchDataSource.pageNameFromUrl("file:///android_asset/stitch_ui/detail.html"));
        assertEquals("detail", BackendStitchDataSource.pageNameFromUrl("file:///android_asset/stitch_ui/detail.html?adId=ad_002"));
        assertEquals("detail", BackendStitchDataSource.pageNameFromUrl("file:///android_asset/stitch_ui/detail.html#top"));
        assertEquals("messages", BackendStitchDataSource.pageNameFromUrl("file:///android_asset/stitch_ui/messages.html"));
        assertEquals("profile", BackendStitchDataSource.pageNameFromUrl("file:///android_asset/stitch_ui/profile.html"));
    }

    @Test
    public void defaultConfigIncludesEmulatorAndAdbReverseCandidates() {
        assertFalse(BackendConfig.defaultCandidates().isEmpty());
        assertEquals("http://10.0.2.2:8080", BackendConfig.defaultCandidates().get(0).getApiBaseUrl());
        assertEquals("http://127.0.0.1:8080", BackendConfig.defaultCandidates().get(1).getApiBaseUrl());
    }

    @Test
    public void unwrapsBackendEnvelopeForWebViewPayload() throws Exception {
        BackendStitchDataSource dataSource = new BackendStitchDataSource(path ->
                "{\"requestId\":\"req-test\",\"code\":\"OK\",\"message\":\"ok\",\"data\":"
                        + "{\"homeFeed\":{\"page\":{\"items\":[]}},\"appConfig\":{}}}");

        JSONObject payload = new JSONObject(dataSource.pagePayloadForUrl("home.html"));

        assertTrue(payload.has("homeFeed"));
        assertTrue(payload.has("appConfig"));
    }

    @Test
    public void rejectsNonOkBackendEnvelope() throws Exception {
        BackendStitchDataSource dataSource = new BackendStitchDataSource(path ->
                "{\"requestId\":\"req-test\",\"code\":\"NOT_FOUND\",\"message\":\"missing\",\"data\":null}");

        try {
            dataSource.pagePayloadForUrl("home.html");
        } catch (RemoteAdException exception) {
            assertEquals(RemoteAdException.Reason.INVALID_RESPONSE, exception.getReason());
            return;
        }
        throw new AssertionError("Expected RemoteAdException");
    }
}
