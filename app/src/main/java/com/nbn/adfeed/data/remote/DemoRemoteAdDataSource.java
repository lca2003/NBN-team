package com.nbn.adfeed.data.remote;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.model.SearchRequest;

public final class DemoRemoteAdDataSource implements RemoteAdDataSource {
    public interface ConnectivityChecker {
        boolean isOnline();
    }

    public static final String SOURCE = "remote";

    private final MockAdRepository backingRepository;
    private final ConnectivityChecker connectivityChecker;

    public DemoRemoteAdDataSource(Context context, MockAdRepository backingRepository) {
        this(backingRepository, () -> isOnline(context));
    }

    public DemoRemoteAdDataSource(MockAdRepository backingRepository, ConnectivityChecker connectivityChecker) {
        this.backingRepository = backingRepository == null ? new MockAdRepository() : backingRepository;
        this.connectivityChecker = connectivityChecker == null ? () -> true : connectivityChecker;
    }

    @Override
    public DataResult<PageResult<AdItem>> loadAds(PageRequest request) throws RemoteAdException {
        requireOnline();
        return asRemotePageResult(backingRepository.loadAds(request));
    }

    @Override
    public DataResult<AdItem> getAdById(String adId) throws RemoteAdException {
        requireOnline();
        return asRemoteItemResult(backingRepository.getAdById(adId));
    }

    @Override
    public DataResult<PageResult<AdItem>> searchAds(SearchRequest request) throws RemoteAdException {
        requireOnline();
        return asRemotePageResult(backingRepository.searchAds(request));
    }

    private void requireOnline() throws RemoteAdException {
        if (!connectivityChecker.isOnline()) {
            throw new RemoteAdException(RemoteAdException.Reason.NETWORK, "Demo remote feed unavailable offline");
        }
    }

    private static DataResult<PageResult<AdItem>> asRemotePageResult(DataResult<PageResult<AdItem>> result) {
        PageResult<AdItem> page = result.getData();
        PageResult<AdItem> remotePage = page == null ? null : new PageResult<>(
                page.getItems(),
                page.getCurrentCursor(),
                page.getNextCursor(),
                page.hasMore(),
                page.getPageNumber(),
                page.getPageSize(),
                page.getTotalCount(),
                SOURCE
        );
        if (result.isEmpty()) {
            return DataResult.empty(remotePage, SOURCE, result.getMessage());
        }
        return DataResult.success(remotePage, SOURCE);
    }

    private static DataResult<AdItem> asRemoteItemResult(DataResult<AdItem> result) {
        if (result.hasData()) {
            return DataResult.success(result.getData(), SOURCE);
        }
        return DataResult.empty(null, SOURCE, result.getMessage());
    }

    private static boolean isOnline(Context context) {
        if (context == null) {
            return true;
        }
        Context appContext = context.getApplicationContext();
        ConnectivityManager connectivityManager = appContext.getSystemService(ConnectivityManager.class);
        if (connectivityManager == null) {
            return false;
        }
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            return false;
        }
        boolean hasTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        return hasTransport && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
