package com.nbn.adfeed.data.repository;

import com.nbn.adfeed.data.model.AdItem;

import java.util.List;

public interface AdRepository {
    List<AdItem> getInitialAds();

    List<AdItem> getAdsByChannel(String channel);

    List<AdItem> searchByKeyword(String keyword);
}
