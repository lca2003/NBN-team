package com.nbn.adfeed.data.repository;

import android.content.Context;

import com.nbn.adfeed.ai.AdAiService;
import com.nbn.adfeed.ai.BackendAdAiService;
import com.nbn.adfeed.ai.DefaultAdAiService;
import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.remote.BackendStitchDataSource;
import com.nbn.adfeed.data.remote.BackendMessageDataSource;
import com.nbn.adfeed.data.remote.BackendPlatformDataSource;
import com.nbn.adfeed.data.remote.BackendRemoteAdDataSource;
import com.nbn.adfeed.data.remote.BackendReviewDataSource;
import com.nbn.adfeed.data.remote.BackendUserDataSource;
import com.nbn.adfeed.data.remote.DemoRemoteAdDataSource;

public final class RepositoryProvider {
    private static AdRepository repository;
    private static AdAiService adAiService;
    private static AnalyticsTracker analyticsTracker;
    private static StitchDataRepository stitchDataRepository;
    private static BackendStitchDataSource backendStitchDataSource;
    private static BackendUserDataSource backendUserDataSource;
    private static BackendMessageDataSource backendMessageDataSource;
    private static BackendReviewDataSource backendReviewDataSource;
    private static BackendPlatformDataSource backendPlatformDataSource;

    private RepositoryProvider() {
    }

    public static synchronized AdRepository getRepository(Context context) {
        if (repository == null) {
            MockAdRepository mockRepository = MockAdRepository.fromAsset(context);
            repository = new DefaultAdRepository(BackendRemoteAdDataSource.defaultDataSource(), mockRepository);
        }
        return repository;
    }

    public static synchronized AdRepository getRepository() {
        if (repository == null) {
            MockAdRepository mockRepository = new MockAdRepository();
            repository = new DefaultAdRepository(new DemoRemoteAdDataSource(mockRepository, () -> true), mockRepository);
        }
        return repository;
    }

    public static synchronized AdAiService getAdAiService(Context context) {
        if (adAiService == null) {
            adAiService = new BackendAdAiService(new DefaultAdAiService(getRepository(context)));
        }
        return adAiService;
    }

    public static synchronized AdAiService getAdAiService() {
        if (adAiService == null) {
            adAiService = new DefaultAdAiService(getRepository());
        }
        return adAiService;
    }

    public static synchronized AnalyticsTracker getAnalyticsTracker() {
        if (analyticsTracker == null) {
            analyticsTracker = new AnalyticsTracker();
        }
        return analyticsTracker;
    }

    public static synchronized StitchDataRepository getStitchDataRepository(Context context) {
        if (stitchDataRepository == null) {
            stitchDataRepository = new StitchDataRepository(context);
        }
        return stitchDataRepository;
    }

    public static synchronized BackendStitchDataSource getBackendStitchDataSource() {
        if (backendStitchDataSource == null) {
            backendStitchDataSource = BackendStitchDataSource.defaultDataSource();
        }
        return backendStitchDataSource;
    }

    public static synchronized BackendUserDataSource getBackendUserDataSource() {
        if (backendUserDataSource == null) {
            backendUserDataSource = BackendUserDataSource.defaultDataSource();
        }
        return backendUserDataSource;
    }

    public static synchronized BackendMessageDataSource getBackendMessageDataSource() {
        if (backendMessageDataSource == null) {
            backendMessageDataSource = BackendMessageDataSource.defaultDataSource();
        }
        return backendMessageDataSource;
    }

    public static synchronized BackendReviewDataSource getBackendReviewDataSource() {
        if (backendReviewDataSource == null) {
            backendReviewDataSource = BackendReviewDataSource.defaultDataSource();
        }
        return backendReviewDataSource;
    }

    public static synchronized BackendPlatformDataSource getBackendPlatformDataSource() {
        if (backendPlatformDataSource == null) {
            backendPlatformDataSource = BackendPlatformDataSource.defaultDataSource();
        }
        return backendPlatformDataSource;
    }

    public static synchronized void resetForTests() {
        repository = null;
        adAiService = null;
        analyticsTracker = null;
        stitchDataRepository = null;
        backendStitchDataSource = null;
        backendUserDataSource = null;
        backendMessageDataSource = null;
        backendReviewDataSource = null;
        backendPlatformDataSource = null;
    }
}
