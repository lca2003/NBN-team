package com.nbn.adfeed.data.repository;

import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.AdPage;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.InteractionAction;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.model.SearchRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public interface AdRepository {
    default DataResult<PageResult<AdItem>> loadAds(PageRequest request) {
        PageRequest safeRequest = request == null
                ? PageRequest.firstPage("", PageRequest.DEFAULT_PAGE_SIZE)
                : request;
        return DataResult.empty(PageResult.empty(safeRequest, "compat"), "compat", "Repository does not support paging");
    }

    default DataResult<AdItem> getAdById(String adId) {
        return DataResult.empty(null, "compat", "Repository does not support getAdById");
    }

    default DataResult<PageResult<AdItem>> searchAds(SearchRequest request) {
        PageRequest pageRequest = request == null
                ? PageRequest.firstPage("", PageRequest.DEFAULT_PAGE_SIZE)
                : request.toPageRequest();
        return DataResult.empty(PageResult.empty(pageRequest, "compat"), "compat", "Repository does not support searchAds");
    }

    default DataResult<AdItem> updateInteraction(String adId, InteractionAction action) {
        return getAdById(adId);
    }

    default List<AdItem> getInitialAds() {
        return pageItems(loadAds(PageRequest.firstPage("", PageRequest.DEFAULT_PAGE_SIZE)));
    }

    default List<AdItem> getAllAdsForStats() {
        List<AdItem> allAds = new ArrayList<>();
        Set<String> visitedCursors = new HashSet<>();
        PageRequest request = new PageRequest(
                "",
                PageRequest.FIRST_CURSOR,
                PageRequest.DEFAULT_PAGE_SIZE,
                true,
                "stats"
        );

        while (visitedCursors.add(request.getCursor())) {
            PageResult<AdItem> page = pageData(loadAds(request));
            if (page == null || page.getItems().isEmpty()) {
                break;
            }

            allAds.addAll(page.getItems());
            String nextCursor = page.getNextCursor();
            if (!page.hasMore() || nextCursor == null || nextCursor.trim().isEmpty()) {
                break;
            }

            request = new PageRequest("", nextCursor, page.getPageSize(), false, "stats");
        }

        return allAds;
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
