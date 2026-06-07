package com.nbn.adfeed.ui.media;

import com.nbn.adfeed.data.mock.MockAdJsonParser;
import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MediaHttpsContractTest {
    @Test
    public void mockAdsUseHttpsMediaInsteadOfAndroidResourceUris() throws Exception {
        List<AdItem> ads = readAds();
        int videoAds = 0;
        Set<String> videoUrls = new HashSet<>();

        for (AdItem ad : ads) {
            assertHttps(ad.getImageUrl());
            assertHttps(ad.getThumbnailUrl());
            assertNoAndroidResource(ad.getImageUrl());
            assertNoAndroidResource(ad.getThumbnailUrl());
            if (ad.getContentType() == AdContentType.VIDEO) {
                videoAds++;
                assertHttps(ad.getVideoUrl());
                assertNoAndroidResource(ad.getVideoUrl());
                videoUrls.add(ad.getVideoUrl());
            }
        }

        assertEquals(30, ads.size());
        assertEquals(6, videoAds);
        assertEquals(6, videoUrls.size());
    }

    @Test
    public void mediaManifestCoversEveryAdAndMatchesBusinessUrls() throws Exception {
        Map<String, AdItem> adsById = new HashMap<>();
        for (AdItem ad : readAds()) {
            adsById.put(ad.getId(), ad);
        }
        JSONArray manifest = readManifest();
        Set<String> manifestIds = new HashSet<>();

        for (int index = 0; index < manifest.length(); index++) {
            JSONObject row = manifest.getJSONObject(index);
            String adId = row.getString("adId");
            AdItem ad = adsById.get(adId);
            assertNotNull(ad);
            manifestIds.add(adId);
            assertEquals(ad.getContentType().name(), row.getString("contentType"));
            assertEquals(ad.getImageUrl(), row.getString("imageUrl"));
            assertEquals(ad.getThumbnailUrl(), row.getString("thumbnailUrl"));
            assertRequiredText(row, "source");
            assertRequiredText(row, "license");
            assertRequiredText(row, "attribution");
            assertTrue(row.getInt("imageWidth") >= 480);
            assertTrue(row.getInt("imageHeight") >= 320);
            assertTrue(row.getInt("thumbnailWidth") >= 240);
            assertTrue(row.getInt("thumbnailHeight") >= 160);
            assertTrue(row.getLong("imageSizeBytes") > 0);
            assertTrue(row.getLong("thumbnailSizeBytes") > 0);

            if (ad.getContentType() == AdContentType.VIDEO) {
                assertEquals(ad.getVideoUrl(), row.getString("videoUrl"));
                assertRequiredText(row, "videoSource");
                assertRequiredText(row, "videoLicense");
                assertRequiredText(row, "videoAttribution");
                assertTrue(row.getInt("videoWidth") >= 640);
                assertTrue(row.getInt("videoHeight") >= 360);
                assertTrue(row.getLong("videoDurationMs") > 0);
                assertTrue(row.getLong("videoSizeBytes") > 0);
            } else {
                assertTrue(row.isNull("videoUrl"));
            }
        }

        assertEquals(adsById.keySet(), manifestIds);
    }

    @Test
    public void mediaLoaderOnlyTreatsHttpsAsRemoteMedia() {
        assertTrue(AdMediaLoader.isHttpsMedia("https://example.com/image.jpg"));
        assertTrue(AdMediaLoader.isHttpsMedia("  https://example.com/image.jpg  "));
        assertFalse(AdMediaLoader.isHttpsMedia("http://example.com/image.jpg"));
        assertFalse(AdMediaLoader.isHttpsMedia("android.resource://com.nbn.adfeed/drawable/ad_media_large_market"));
        assertFalse(AdMediaLoader.isHttpsMedia(null));
    }

    @Test
    public void localRawVideoUrisAreConvertedForMedia3() {
        String fromRawPath = AdMediaResources.playableVideoUri("raw/ad_video_headphones.mp4");
        String fromAndroidResPath = AdMediaResources.playableVideoUri(
                "file:///android_res/raw/ad_video_headphones.mp4");
        String fromAndroidResourceUri = AdMediaResources.playableVideoUri(
                "android.resource://com.nbn.adfeed/raw/ad_video_headphones.mp4");

        assertNotNull(fromRawPath);
        assertTrue(fromRawPath, fromRawPath.startsWith("rawresource:///"));
        assertEquals(fromRawPath, fromAndroidResPath);
        assertEquals(fromRawPath, fromAndroidResourceUri);
        assertEquals("https://example.com/video.mp4",
                AdMediaResources.playableVideoUri(" https://example.com/video.mp4 "));
        assertNull(AdMediaResources.playableVideoUri("  "));
    }

    @Test
    public void knownMockVideoAdsPreferPackagedRawPlayback() throws Exception {
        AdItem videoAd = null;
        for (AdItem ad : readAds()) {
            if ("ad_003".equals(ad.getId())) {
                videoAd = ad;
                break;
            }
        }

        assertNotNull(videoAd);
        assertTrue(videoAd.getVideoUrl(), videoAd.getVideoUrl().startsWith("https://"));
        String playableUri = AdMediaResources.playableVideoUri(videoAd);
        assertNotNull(playableUri);
        assertTrue(playableUri, playableUri.startsWith("rawresource:///"));
    }

    private static List<AdItem> readAds() throws Exception {
        return MockAdJsonParser.parse(new String(Files.readAllBytes(Path.of("src/main/assets/ads_mock.json"))));
    }

    private static JSONArray readManifest() throws Exception {
        return new JSONArray(new String(Files.readAllBytes(Path.of("src/main/assets/media_manifest.json"))));
    }

    private static void assertHttps(String url) {
        assertNotNull(url);
        assertTrue(url, url.startsWith("https://"));
    }

    private static void assertNoAndroidResource(String url) {
        assertFalse(url, url.contains("android.resource://"));
    }

    private static void assertRequiredText(JSONObject row, String key) {
        String value = row.optString(key, "");
        assertFalse(key, value.trim().isEmpty());
        assertFalse(key, "unknown".equalsIgnoreCase(value.trim()));
    }
}
