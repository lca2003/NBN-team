package com.nbn.adfeed.data.repository;

import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.AdPage;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.InteractionAction;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.model.SearchRequest;

import java.util.Collections;
import java.util.List;

public interface AdRepository {
    DataResult<PageResult<AdItem>> loadAds(PageRequest request);

    DataResult<AdItem> getAdById(String adId);

    DataResult<PageResult<AdItem>> searchAds(SearchRequest request);

    DataResult<AdItem> updateInteraction(String adId, InteractionAction action);

    default List<AdItem> getInitialAds() {
        return pageItems(loadAds(PageRequest.firstPage("", PageRequest.DEFAULT_PAGE_SIZE)));
    }

    default List<AdItem> getAdsByChannel(String channel) {
        return pageItems(loadAds(PageRequest.firstPage(channel, PageRequest.DEFAULT_PAGE_SIZE)));
    }

    default AdPage getAdsPage(PageRequest request) {
        PageRequest safeRequest = request == null
                ? PageRequest.firstPage("", PageRequest.DEFAULT_PAGE_SIZE)
                : request;
        PageResult<AdItem> page = pageData(loadAds(safeRequest));
        if (page == null) {
            return new AdPage(Collections.emptyList(), null, false);
        }
        return new AdPage(page.getItems(), page.getNextCursor(), page.hasMore());
    }

    default List<AdItem> getAdsByTag(String tag) {
        return pageItems(searchAds(SearchRequest.tag(tag)));
    }

    default List<AdItem> searchByKeyword(String keyword) {
        return pageItems(searchAds(SearchRequest.keyword(keyword)));
    }

    private static List<AdItem> pageItems(DataResult<PageResult<AdItem>> result) {
        PageResult<AdItem> page = pageData(result);
        return page == null ? Collections.emptyList() : page.getItems();
    }

    private static PageResult<AdItem> pageData(DataResult<PageResult<AdItem>> result) {
        return result == null ? null : result.getData();
    }
}
