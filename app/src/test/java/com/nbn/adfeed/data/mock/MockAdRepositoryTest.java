package com.nbn.adfeed.data.mock;

import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.AdPage;
import com.nbn.adfeed.data.model.PageRequest;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class MockAdRepositoryTest {
    private final MockAdRepository repository = new MockAdRepository();

    @Test
    public void getAdsPageReturnsCursorBasedPages() {
        AdPage firstPage = repository.getAdsPage(PageRequest.firstPage(null, 2));

        assertEquals(2, firstPage.getItems().size());
        assertTrue(firstPage.hasMore());
        assertEquals("page_2", firstPage.getNextCursor());

        AdPage secondPage = repository.getAdsPage(new PageRequest(null, firstPage.getNextCursor(), 2));

        assertEquals(2, secondPage.getItems().size());
        assertEquals("ad_003", secondPage.getItems().get(0).getId());
    }

    @Test
    public void getAdsByChannelFiltersStableMockData() {
        List<AdItem> localAds = repository.getAdsByChannel("本地");

        assertFalse(localAds.isEmpty());
        for (AdItem ad : localAds) {
            assertEquals("本地", ad.getChannel());
        }
    }

    @Test
    public void searchByKeywordMatchesTagsAndDescriptions() {
        List<AdItem> result = repository.searchByKeyword("找适合学生党的运动广告");

        assertTrue(containsAd(result, "ad_001"));
    }

    @Test
    public void getAdByIdExposesMediaFieldsForFeedCards() {
        AdItem videoAd = repository.getAdById("ad_003");

        assertNotNull(videoAd);
        assertNotNull(videoAd.getImageUrl());
        assertNotNull(videoAd.getVideoUrl());
    }

    private static boolean containsAd(List<AdItem> ads, String adId) {
        for (AdItem ad : ads) {
            if (ad.getId().equals(adId)) {
                return true;
            }
        }
        return false;
    }
}
