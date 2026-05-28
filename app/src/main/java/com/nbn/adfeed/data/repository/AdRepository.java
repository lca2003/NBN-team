package com.nbn.adfeed.data.repository;

import com.nbn.adfeed.data.model.AdPage;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.PageRequest;

import java.util.List;

public interface AdRepository {
    List<AdItem> getInitialAds();

    List<AdItem> getAdsByChannel(String channel);

    AdPage getAdsPage(PageRequest request);

    AdItem getAdById(String adId);

    List<AdItem> getAdsByTag(String tag);

    List<AdItem> searchByKeyword(String keyword);
}
