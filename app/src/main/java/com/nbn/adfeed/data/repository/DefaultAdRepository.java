package com.nbn.adfeed.data.repository;

import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.InteractionAction;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.model.SearchRequest;
import com.nbn.adfeed.data.remote.RemoteAdDataSource;
import com.nbn.adfeed.data.remote.RemoteAdException;

public final class DefaultAdRepository implements AdRepository {
    private final RemoteAdDataSource remoteDataSource;
    private final MockAdRepository mockRepository;

    public DefaultAdRepository(MockAdRepository mockRepository) {
        this(null, mockRepository);
    }

    public DefaultAdRepository(RemoteAdDataSource remoteDataSource, MockAdRepository mockRepository) {
        this.remoteDataSource = remoteDataSource;
        this.mockRepository = mockRepository == null ? new MockAdRepository() : mockRepository;
    }

    @Override
    public DataResult<PageResult<AdItem>> loadAds(PageRequest request) {
        if (remoteDataSource == null) {
            return mockRepository.loadAds(request);
        }
        try {
            DataResult<PageResult<AdItem>> remote = remoteDataSource.loadAds(request);
            if (remote != null && remote.isSuccess() && remote.hasData()) {
                return remote;
            }
            DataResult<PageResult<AdItem>> fallback = mockRepository.loadAds(request);
            return DataResult.fallback(fallback.getData(), MockAdRepository.SOURCE, "Remote returned no usable feed data", null);
        } catch (RemoteAdException exception) {
            return fallbackLoad(request, exception);
        }
    }

    @Override
    public DataResult<AdItem> getAdById(String adId) {
        if (remoteDataSource == null) {
            return mockRepository.getAdById(adId);
        }
        try {
            DataResult<AdItem> remote = remoteDataSource.getAdById(adId);
            if (remote != null && remote.isSuccess() && remote.hasData()) {
                return remote;
            }
            DataResult<AdItem> fallback = mockRepository.getAdById(adId);
            return fallback.hasData()
                    ? DataResult.fallback(fallback.getData(), MockAdRepository.SOURCE, "Remote detail unavailable", null)
                    : fallback;
        } catch (RemoteAdException exception) {
            DataResult<AdItem> fallback = mockRepository.getAdById(adId);
            return fallback.hasData()
                    ? DataResult.fallback(fallback.getData(), MockAdRepository.SOURCE, exception.getMessage(), exception)
                    : fallback;
        }
    }

    @Override
    public DataResult<PageResult<AdItem>> searchAds(SearchRequest request) {
        if (remoteDataSource == null) {
            return mockRepository.searchAds(request);
        }
        try {
            DataResult<PageResult<AdItem>> remote = remoteDataSource.searchAds(request);
            if (remote != null && remote.isSuccess() && remote.hasData()) {
                return remote;
            }
            DataResult<PageResult<AdItem>> fallback = mockRepository.searchAds(request);
            return DataResult.fallback(fallback.getData(), MockAdRepository.SOURCE, "Remote search unavailable", null);
        } catch (RemoteAdException exception) {
            DataResult<PageResult<AdItem>> fallback = mockRepository.searchAds(request);
            return DataResult.fallback(fallback.getData(), MockAdRepository.SOURCE, exception.getMessage(), exception);
        }
    }

    @Override
    public DataResult<AdItem> updateInteraction(String adId, InteractionAction action) {
        if (remoteDataSource != null) {
            try {
                DataResult<AdItem> remote = remoteDataSource.updateInteraction(adId, action);
                if (remote != null && remote.isSuccess() && remote.hasData()) {
                    return remote;
                }
            } catch (RemoteAdException exception) {
                DataResult<AdItem> fallback = mockRepository.updateInteraction(adId, action);
                return fallback.hasData()
                        ? DataResult.fallback(fallback.getData(), MockAdRepository.SOURCE, exception.getMessage(), exception)
                        : fallback;
            }
        }
        return mockRepository.updateInteraction(adId, action);
    }

    private DataResult<PageResult<AdItem>> fallbackLoad(PageRequest request, RemoteAdException exception) {
        DataResult<PageResult<AdItem>> fallback = mockRepository.loadAds(request);
        if (fallback.hasData()) {
            return DataResult.fallback(fallback.getData(), MockAdRepository.SOURCE, exception.getMessage(), exception);
        }
        return fallback;
    }
}
