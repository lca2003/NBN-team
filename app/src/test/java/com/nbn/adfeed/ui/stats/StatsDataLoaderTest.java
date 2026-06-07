package com.nbn.adfeed.ui.stats;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;

import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.repository.AdRepository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class StatsDataLoaderTest {

    @Test
    public void loadsRepositoryOffMainThreadAndPostsResultOnMainThread() throws Exception {
        AtomicBoolean repositoryRanOffMainThread = new AtomicBoolean(false);
        CountDownLatch repositoryCalled = new CountDownLatch(1);
        AtomicBoolean callbackRanOnMainThread = new AtomicBoolean(false);
        AtomicReference<List<AdItem>> loadedAds = new AtomicReference<>();
        AdRepository repository = new AdRepository() {
            @Override
            public List<AdItem> getAllAdsForStats() {
                repositoryRanOffMainThread.set(Looper.myLooper() != Looper.getMainLooper());
                repositoryCalled.countDown();
                return Collections.emptyList();
            }
        };
        StatsDataLoader loader = new StatsDataLoader();

        loader.load(repository, new AnalyticsTracker(), (ads, eventCounts) -> {
            callbackRanOnMainThread.set(Looper.myLooper() == Looper.getMainLooper());
            loadedAds.set(ads);
        });

        assertTrue(repositoryCalled.await(5, TimeUnit.SECONDS));
        for (int attempt = 0; attempt < 50 && loadedAds.get() == null; attempt++) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10L);
        }

        assertTrue(repositoryRanOffMainThread.get());
        assertNotNull(loadedAds.get());
        assertTrue(callbackRanOnMainThread.get());
        loader.close();
    }

    @Test
    public void loadsStatsWithSingleLargePageRequest() throws Exception {
        CountDownLatch repositoryCalled = new CountDownLatch(1);
        AtomicReference<PageRequest> capturedRequest = new AtomicReference<>();
        AdRepository repository = new AdRepository() {
            @Override
            public DataResult<PageResult<AdItem>> loadAds(PageRequest request) {
                capturedRequest.set(request);
                repositoryCalled.countDown();
                return DataResult.empty(PageResult.empty(request, "test"), "test", "empty");
            }
        };
        StatsDataLoader loader = new StatsDataLoader();

        loader.load(repository, null, (ads, eventCounts) -> { });

        assertTrue(repositoryCalled.await(5, TimeUnit.SECONDS));
        assertNotNull(capturedRequest.get());
        assertEquals(1000, capturedRequest.get().getPageSize());
        assertEquals(PageRequest.FIRST_CURSOR, capturedRequest.get().getCursor());
        loader.close();
    }
}
