package com.nbn.adfeed.data.repository;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class StitchDataRepositoryTest {
    @After
    public void tearDown() {
        RepositoryProvider.resetForTests();
    }

    @Test
    public void providerReusesStitchDataRepository() {
        StitchDataRepository first = RepositoryProvider.getStitchDataRepository(RuntimeEnvironment.getApplication());
        StitchDataRepository second = RepositoryProvider.getStitchDataRepository(RuntimeEnvironment.getApplication());

        assertSame(first, second);
        RepositoryProvider.resetForTests();
        assertNotSame(first, RepositoryProvider.getStitchDataRepository(RuntimeEnvironment.getApplication()));
    }

    @Test
    public void repositoryLoadsAllStitchJsonDomains() throws Exception {
        StitchDataRepository repository = RepositoryProvider.getStitchDataRepository(RuntimeEnvironment.getApplication());

        assertTrue(new JSONObject(repository.homeFeedJson()).getJSONArray("channels").length() >= 3);
        assertTrue(new JSONObject(repository.adDetailsJson()).getJSONArray("details").length() >= 2);
        assertTrue(new JSONObject(repository.searchResultsJson()).getJSONArray("suggestions").length() >= 4);
        assertTrue(new JSONObject(repository.messagesJson()).getJSONArray("conversations").length() >= 2);
        assertTrue(new JSONObject(repository.profileJson()).getJSONArray("posts").length() >= 3);
        assertTrue(new JSONObject(repository.reviewsJson()).getJSONObject("reviewsByAd").length() >= 2);
        assertTrue(new JSONObject(repository.appConfigJson()).getJSONArray("assetManifest").length() >= 3);
    }

    @Test
    public void pagePayloadMapsWebAssetsToCurrentTabData() throws Exception {
        StitchDataRepository repository = RepositoryProvider.getStitchDataRepository(RuntimeEnvironment.getApplication());

        JSONObject home = new JSONObject(repository.pagePayloadForUrl("file:///android_asset/stitch_ui/home.html"));
        JSONObject search = new JSONObject(repository.pagePayloadForUrl("file:///android_asset/stitch_ui/search.html"));
        JSONObject detail = new JSONObject(repository.pagePayloadForUrl("file:///android_asset/stitch_ui/detail.html"));
        JSONObject detailWithAdId = new JSONObject(repository.pagePayloadForUrl("file:///android_asset/stitch_ui/detail.html?adId=ad_002"));
        JSONObject messages = new JSONObject(repository.pagePayloadForUrl("file:///android_asset/stitch_ui/messages.html"));
        JSONObject profile = new JSONObject(repository.pagePayloadForUrl("file:///android_asset/stitch_ui/profile.html"));

        assertTrue(home.has("homeFeed"));
        assertTrue(search.has("search"));
        assertTrue(detail.has("details"));
        assertTrue(detail.has("reviews"));
        assertTrue(detailWithAdId.has("details"));
        assertTrue(detailWithAdId.has("reviews"));
        assertTrue(messages.has("messages"));
        assertTrue(profile.has("profile"));
        assertTrue(profile.has("appConfig"));
    }
}
