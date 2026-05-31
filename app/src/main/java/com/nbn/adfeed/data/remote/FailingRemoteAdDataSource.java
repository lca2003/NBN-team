package com.nbn.adfeed.data.remote;

import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.model.SearchRequest;

public final class FailingRemoteAdDataSource implements RemoteAdDataSource {
    private final RemoteAdException exception;

    public FailingRemoteAdDataSource() {
        this(new RemoteAdException(RemoteAdException.Reason.NETWORK, "Remote data source is not configured"));
    }

    public FailingRemoteAdDataSource(RemoteAdException exception) {
        this.exception = exception;
    }

    @Override
    public DataResult<PageResult<AdItem>> loadAds(PageRequest request) throws RemoteAdException {
        throw exception;
    }

    @Override
    public DataResult<AdItem> getAdById(String adId) throws RemoteAdException {
        throw exception;
    }

    @Override
    public DataResult<PageResult<AdItem>> searchAds(SearchRequest request) throws RemoteAdException {
        throw exception;
    }
}
