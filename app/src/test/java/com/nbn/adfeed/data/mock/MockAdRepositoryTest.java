package com.nbn.adfeed.data.mock;

import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.InteractionState;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.model.SearchRequest;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class MockAdRepositoryTest {
    private final MockAdRepository repository = new MockAdRepository();

    @Test
    public void loadAdsReturnsCursorBasedPages() {
        DataResult<PageResult<AdItem>> firstResult = repository.loadAds(PageRequest.firstPage("", 10));

        assertEquals(DataResult.Status.SUCCESS, firstResult.getStatus());
        PageResult<AdItem> firstPage = firstResult.getData();
        assertEquals(10, firstPage.getItems().size());
        assertTrue(firstPage.hasMore());
        assertEquals("page_2", firstPage.getNextCursor());

        DataResult<PageResult<AdItem>> secondResult = repository.loadAds(
                PageRequest.nextPage("", firstPage.getNextCursor(), 10)
        );

        assertEquals(10, secondResult.getData().getItems().size());
        assertEquals("ad_011", secondResult.getData().getItems().get(0).getId());

        Set<String> firstPageIds = new HashSet<>();
        for (AdItem ad : firstPage.getItems()) {
            firstPageIds.add(ad.getId());
        }
        for (AdItem ad : secondResult.getData().getItems()) {
            assertFalse(firstPageIds.contains(ad.getId()));
        }
    }

    @Test
    public void loadAdsSupportsLastAndOutOfRangePages() {
        PageResult<AdItem> lastPage = repository.loadAds(new PageRequest("", "page_3", 10)).getData();
        assertEquals(10, lastPage.getItems().size());
        assertFalse(lastPage.hasMore());

        DataResult<PageResult<AdItem>> outOfRange = repository.loadAds(new PageRequest("", "page_4", 10));
        assertEquals(DataResult.Status.EMPTY, outOfRange.getStatus());
        assertTrue(outOfRange.getData().getItems().isEmpty());
        assertFalse(outOfRange.getData().hasMore());
    }

    @Test
    public void getAllAdsForStatsReturnsEveryMockAd() {
        List<AdItem> statsAds = repository.getAllAdsForStats();

        assertEquals(30, statsAds.size());
        assertEquals("ad_001", statsAds.get(0).getId());
        assertEquals("ad_030", statsAds.get(29).getId());
    }

    @Test
    public void channelCoverageHasAtLeastEightAdsEach() {
        Map<String, Integer> counts = new HashMap<>();
        for (AdItem ad : repository.snapshot()) {
            counts.put(ad.getChannel(), counts.getOrDefault(ad.getChannel(), 0) + 1);
        }

        assertTrue(counts.get("精选") >= 8);
        assertTrue(counts.get("电商") >= 8);
        assertTrue(counts.get("本地") >= 8);
    }

    @Test
    public void jsonAssetParsesAndCoversMediaTypes() throws Exception {
        String json = new String(Files.readAllBytes(Path.of("src/main/assets/ads_mock.json")));
        List<AdItem> ads = MockAdJsonParser.parse(json);
        EnumSet<AdContentType> types = EnumSet.noneOf(AdContentType.class);
        int commerceAdsWithCta = 0;

        for (AdItem ad : ads) {
            types.add(ad.getContentType());
            assertTrue(ad.getTags().size() >= 3);
            assertTrue(ad.getTags().size() <= 5);
            assertFalse(ad.getAssetTheme().isEmpty());
            assertFalse(ad.getVisualLabel().isEmpty());
            assertFalse(ad.getCtaIntent().isEmpty());
            if ("电商".equals(ad.getChannel())) {
                assertFalse(ad.getOfferText().isEmpty());
                assertEquals("查看商品", ad.getCtaText());
                assertFalse(ad.getSkuText().isEmpty());
                assertFalse(ad.getRatingText().isEmpty());
                assertFalse(ad.getDeliveryText().isEmpty());
                assertFalse(ad.getStockText().isEmpty());
                assertTrue(ad.getSimilarItems().size() >= 2);
                commerceAdsWithCta++;
            }
            if ("本地".equals(ad.getChannel())) {
                assertFalse(ad.getDistanceText().isEmpty());
                assertFalse(ad.getDistrictText().isEmpty());
                assertFalse(ad.getAddressText().isEmpty());
                assertFalse(ad.getBusinessHoursText().isEmpty());
                assertFalse(ad.getNavigationText().isEmpty());
            }
        }

        assertEquals(30, ads.size());
        assertTrue(commerceAdsWithCta >= 8);
        assertTrue(types.contains(AdContentType.LARGE_IMAGE));
        assertTrue(types.contains(AdContentType.SMALL_IMAGE));
        assertTrue(types.contains(AdContentType.VIDEO));
    }

    @Test
    public void defaultConstructorUsesAssetAsPrimaryFactSource() throws Exception {
        List<AdItem> expectedAds = MockAdJsonParser.parse(
                new String(Files.readAllBytes(Path.of("src/main/assets/ads_mock.json")))
        );
        List<AdItem> actualAds = new MockAdRepository().snapshot();

        assertSameShape(expectedAds, actualAds);
    }

    @Test
    public void loadDefaultAdsPrefersJsonLoaderOverFixturesFallback() throws Exception {
        final boolean[] fallbackCalled = {false};
        String assetJson = new String(Files.readAllBytes(Path.of("src/main/assets/ads_mock.json")));

        List<AdItem> ads = MockAdRepository.loadDefaultAds(
                () -> assetJson,
                () -> {
                    fallbackCalled[0] = true;
                    return Collections.singletonList(fallbackAd("fallback_only"));
                }
        );

        assertFalse(fallbackCalled[0]);
        assertSameShape(MockAdJsonParser.parse(assetJson), ads);
    }

    @Test
    public void loadDefaultAdsFallsBackToFixturesWhenJsonLoaderReturnsNull() {
        final boolean[] fallbackCalled = {false};
        List<AdItem> fallbackAds = Collections.singletonList(fallbackAd("fallback_null"));

        List<AdItem> ads = MockAdRepository.loadDefaultAds(
                () -> null,
                () -> {
                    fallbackCalled[0] = true;
                    return fallbackAds;
                }
        );

        assertTrue(fallbackCalled[0]);
        assertSame(fallbackAds, ads);
        assertEquals("fallback_null", ads.get(0).getId());
    }

    @Test
    public void loadDefaultAdsFallsBackToFixturesWhenJsonLoaderThrows() {
        final boolean[] fallbackCalled = {false};
        List<AdItem> fallbackAds = Collections.singletonList(fallbackAd("fallback_error"));

        List<AdItem> ads = MockAdRepository.loadDefaultAds(
                () -> {
                    throw new java.io.IOException("asset missing");
                },
                () -> {
                    fallbackCalled[0] = true;
                    return fallbackAds;
                }
        );

        assertTrue(fallbackCalled[0]);
        assertSame(fallbackAds, ads);
        assertEquals("fallback_error", ads.get(0).getId());
    }

    @Test
    public void fixedKeywordsHaveStableSearchHits() {
        assertSearchHit("学生党");
        assertSearchHit("运动");
        assertSearchHit("咖啡");
        assertSearchHit("数码");
        assertSearchHit("通勤");
    }

    @Test
    public void searchMatchesTitleBrandDescriptionAndTags() {
        assertSearchHit("轻量跑鞋");
        assertSearchHit("City Cafe");
        assertSearchHit("通勤降噪");
        assertFalse(repository.searchAds(SearchRequest.tag("咖啡")).getData().getItems().isEmpty());
    }

    @Test
    public void getAdByIdReturnsControllableEmptyResult() {
        DataResult<AdItem> result = repository.getAdById("missing");

        assertEquals(DataResult.Status.EMPTY, result.getStatus());
        assertFalse(result.hasData());
        assertNotNull(result.getMessage());
    }

    private void assertSearchHit(String keyword) {
        DataResult<PageResult<AdItem>> result = repository.searchAds(SearchRequest.keyword(keyword));

        assertEquals(DataResult.Status.SUCCESS, result.getStatus());
        assertFalse(result.getData().getItems().isEmpty());
    }

    private static void assertSameShape(List<AdItem> expectedAds, List<AdItem> actualAds) {
        assertEquals(expectedAds.size(), actualAds.size());
        for (int index = 0; index < expectedAds.size(); index++) {
            AdItem expected = expectedAds.get(index);
            AdItem actual = actualAds.get(index);
            assertEquals(expected.getId(), actual.getId());
            assertEquals(expected.getTitle(), actual.getTitle());
            assertEquals(expected.getBrand(), actual.getBrand());
            assertEquals(expected.getChannel(), actual.getChannel());
            assertEquals(expected.getChannelId(), actual.getChannelId());
            assertEquals(expected.getDescription(), actual.getDescription());
            assertEquals(expected.getSummary(), actual.getSummary());
            assertEquals(expected.getImageUrl(), actual.getImageUrl());
            assertEquals(expected.getThumbnailUrl(), actual.getThumbnailUrl());
            assertEquals(expected.getVideoUrl(), actual.getVideoUrl());
            assertEquals(expected.getOfferText(), actual.getOfferText());
            assertEquals(expected.getCtaText(), actual.getCtaText());
            assertEquals(expected.getSkuText(), actual.getSkuText());
            assertEquals(expected.getRatingText(), actual.getRatingText());
            assertEquals(expected.getDeliveryText(), actual.getDeliveryText());
            assertEquals(expected.getStockText(), actual.getStockText());
            assertEquals(expected.getSimilarItems(), actual.getSimilarItems());
            assertEquals(expected.getDistanceText(), actual.getDistanceText());
            assertEquals(expected.getDistrictText(), actual.getDistrictText());
            assertEquals(expected.getAddressText(), actual.getAddressText());
            assertEquals(expected.getBusinessHoursText(), actual.getBusinessHoursText());
            assertEquals(expected.getNavigationText(), actual.getNavigationText());
            assertEquals(expected.getAssetTheme(), actual.getAssetTheme());
            assertEquals(expected.getVisualLabel(), actual.getVisualLabel());
            assertEquals(expected.getCtaIntent(), actual.getCtaIntent());
            assertEquals(expected.getContentType(), actual.getContentType());
            assertEquals(expected.getTags(), actual.getTags());
            assertEquals(expected.getInteractionState().isLiked(), actual.getInteractionState().isLiked());
            assertEquals(expected.getInteractionState().isCollected(), actual.getInteractionState().isCollected());
            assertEquals(expected.getStats().getExposureCount(), actual.getStats().getExposureCount());
            assertEquals(expected.getStats().getClickCount(), actual.getStats().getClickCount());
            assertEquals(expected.getStats().getLikeCount(), actual.getStats().getLikeCount());
            assertEquals(expected.getStats().getCollectCount(), actual.getStats().getCollectCount());
            assertEquals(expected.getStats().getShareCount(), actual.getStats().getShareCount());
            assertEquals(expected.getContentHash(), actual.getContentHash());
        }
    }

    private static AdItem fallbackAd(String id) {
        return new AdItem(
                id,
                "fallback",
                "fallback",
                "精选",
                "channel_featured",
                "fallback description",
                "fallback summary",
                null,
                null,
                null,
                AdContentType.LARGE_IMAGE,
                Collections.singletonList("fallback"),
                InteractionState.empty(),
                null,
                null
        );
    }
}
