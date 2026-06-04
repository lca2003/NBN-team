package com.nbn.adfeed.data.remote;

import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.InteractionAction;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.model.SearchRequest;

public interface RemoteAdDataSource {
    DataResult<PageResult<AdItem>> loadAds(PageRequest request) throws RemoteAdException;

    DataResult<AdItem> getAdById(String adId) throws RemoteAdException;

    DataResult<PageResult<AdItem>> searchAds(SearchRequest request) throws RemoteAdException;

    default DataResult<AdItem> updateInteraction(String adId, InteractionAction action) throws RemoteAdException {
        throw new RemoteAdException(RemoteAdException.Reason.UNKNOWN, "Remote interaction is not supported");
    }
}
