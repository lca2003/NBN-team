package com.nbn.adfeed.data.model;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StitchDataContractTest {
    private static final String ROOT = "src/main/assets/stitch_data/";

    @Test
    public void stitchDataFilesArePresentAndParseable() throws Exception {
        assertJsonObject("home_feed.json");
        assertJsonObject("ad_details.json");
        assertJsonObject("search_results.json");
        assertJsonObject("messages.json");
        assertJsonObject("profile.json");
        assertJsonObject("reviews.json");
        assertJsonObject("app_config.json");
    }

    @Test
    public void homeFeedCoversChannelsAndCards() throws Exception {
        JSONObject home = readObject("home_feed.json");
        JSONArray channels = home.getJSONArray("channels");
        JSONObject page = home.getJSONObject("page");
        JSONArray items = page.getJSONArray("items");

        assertEquals(3, channels.length());
        assertFalse(page.getBoolean("hasMore"));
        assertEquals(items.length(), page.getInt("totalCount"));
        assertTrue(items.length() >= 3);
        assertRequiredText(items.getJSONObject(0), "adId");
        assertRequiredText(items.getJSONObject(0).getJSONObject("cover"), "url");
        assertTrue(items.getJSONObject(0).getJSONArray("tags").length() >= 3);
        assertTrue(items.getJSONObject(0).getJSONObject("stats").getInt("exposureCount") > 0);
    }

    @Test
    public void searchMessagesProfileAndConfigExposeRequiredDomains() throws Exception {
        JSONObject search = readObject("search_results.json");
        JSONObject messages = readObject("messages.json");
        JSONObject profile = readObject("profile.json");
        JSONObject config = readObject("app_config.json");

        assertTrue(search.getJSONArray("suggestions").length() >= 4);
        assertTrue(search.getJSONArray("results").length() >= 2);
        assertRequiredText(search.getJSONObject("fallback"), "message");
        assertTrue(messages.getJSONObject("notificationSummary").getInt("likeUnreadCount") >= 0);
        assertTrue(messages.getJSONArray("conversations").length() > 0);
        assertRequiredText(profile.getJSONObject("userProfile"), "userId");
        assertTrue(profile.getJSONArray("posts").length() >= 3);
        assertTrue(config.getJSONObject("remoteConfig").getBoolean("aiEnabled"));
        assertTrue(config.getJSONArray("assetManifest").length() > 0);
    }

    @Test
    public void detailsAndReviewsCanJoinByAdId() throws Exception {
        JSONObject details = readObject("ad_details.json");
        JSONObject reviews = readObject("reviews.json");
        JSONArray detailRows = details.getJSONArray("details");

        for (int index = 0; index < detailRows.length(); index++) {
            JSONObject detail = detailRows.getJSONObject(index);
            String adId = detail.getString("adId");
            assertTrue(reviews.getJSONObject("reviewsByAd").has(adId));
            assertTrue(detail.getJSONArray("sellingPoints").length() >= 2);
            assertRequiredText(detail.getJSONObject("merchant"), "name");
            assertRequiredText(detail.getJSONObject("offer"), "ctaText");
        }
    }

    private static JSONObject assertJsonObject(String fileName) throws Exception {
        JSONObject object = readObject(fileName);
        assertTrue(fileName, object.length() > 0);
        return object;
    }

    private static JSONObject readObject(String fileName) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(Path.of(ROOT + fileName)), StandardCharsets.UTF_8));
    }

    private static void assertRequiredText(JSONObject object, String key) {
        assertFalse(key, object.optString(key, "").trim().isEmpty());
    }
}
