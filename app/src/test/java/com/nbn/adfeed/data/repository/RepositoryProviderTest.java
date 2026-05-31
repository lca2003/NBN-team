package com.nbn.adfeed.data.repository;

import com.nbn.adfeed.ai.AdAiService;
import com.nbn.adfeed.ai.AiOutputSource;
import com.nbn.adfeed.ai.AiResponse;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.remote.BackendMessageDataSource;
import com.nbn.adfeed.data.remote.BackendPlatformDataSource;
import com.nbn.adfeed.data.remote.BackendReviewDataSource;
import com.nbn.adfeed.data.remote.BackendStitchDataSource;
import com.nbn.adfeed.data.remote.BackendUserDataSource;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class RepositoryProviderTest {
    @After
    public void tearDown() {
        System.clearProperty("nbn.api.baseUrl");
        System.clearProperty("nbn.api.connectTimeoutMs");
        System.clearProperty("nbn.api.readTimeoutMs");
        System.clearProperty("nbn.api.retryCount");
        RepositoryProvider.resetForTests();
    }

    @Test
    public void aiServiceIsSharedAndReusesCacheAcrossCalls() {
        AdAiService firstService = RepositoryProvider.getAdAiService();
        AiResponse<String> firstSummary = firstService.getAiSummary("ad_001");

        AdAiService secondService = RepositoryProvider.getAdAiService();
        AiResponse<String> cachedSummary = secondService.getAiSummary("ad_001");

        assertSame(firstService, secondService);
        assertEquals(AiOutputSource.REMOTE_AI, firstSummary.getSource());
        assertEquals(AiOutputSource.CACHE, cachedSummary.getSource());
    }

    @Test
    public void backendStitchDataSourceIsShared() {
        BackendStitchDataSource first = RepositoryProvider.getBackendStitchDataSource();
        BackendStitchDataSource second = RepositoryProvider.getBackendStitchDataSource();

        assertSame(first, second);
    }

    @Test
    public void backendUserDataSourceIsShared() {
        BackendUserDataSource first = RepositoryProvider.getBackendUserDataSource();
        BackendUserDataSource second = RepositoryProvider.getBackendUserDataSource();

        assertSame(first, second);
    }

    @Test
    public void remainingBackendDataSourcesAreShared() {
        BackendMessageDataSource firstMessage = RepositoryProvider.getBackendMessageDataSource();
        BackendMessageDataSource secondMessage = RepositoryProvider.getBackendMessageDataSource();
        BackendReviewDataSource firstReview = RepositoryProvider.getBackendReviewDataSource();
        BackendReviewDataSource secondReview = RepositoryProvider.getBackendReviewDataSource();
        BackendPlatformDataSource firstPlatform = RepositoryProvider.getBackendPlatformDataSource();
        BackendPlatformDataSource secondPlatform = RepositoryProvider.getBackendPlatformDataSource();

        assertSame(firstMessage, secondMessage);
        assertSame(firstReview, secondReview);
        assertSame(firstPlatform, secondPlatform);
    }

    @Test
    public void contextRepositoryUsesBackendRemoteWithMockFallback() {
        System.setProperty("nbn.api.baseUrl", "http://127.0.0.1:9");
        System.setProperty("nbn.api.connectTimeoutMs", "250");
        System.setProperty("nbn.api.readTimeoutMs", "250");
        System.setProperty("nbn.api.retryCount", "0");
        RepositoryProvider.resetForTests();

        AdRepository repository = RepositoryProvider.getRepository(
                org.robolectric.RuntimeEnvironment.getApplication()
        );

        assertEquals(DataResult.Status.FALLBACK,
                repository.loadAds(com.nbn.adfeed.data.model.PageRequest.firstPage("featured", 1)).getStatus());
    }
}
