package com.nbn.adfeed.data.remote;

import com.nbn.adfeed.data.model.AdPage;
import com.nbn.adfeed.data.model.PageRequest;

public interface RemoteAdDataSource {
    AdPage fetchAds(PageRequest request) throws RemoteAdException;
}
