package com.nbn.adfeed.data.repository;

import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.InteractionAction;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.model.SearchRequest;
import com.nbn.adfeed.data.remote.DemoRemoteAdDataSource;
import com.nbn.adfeed.data.remote.FailingRemoteAdDataSource;
import com.nbn.adfeed.data.remote.RemoteAdDataSource;
import com.nbn.adfeed.data.remote.RemoteAdException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DefaultAdRepositoryTest {
    @Test
    public void remoteFailureFallsBackToMockFeed() {
        DefaultAdRepository repository = new DefaultAdRepository(new FailingRemoteAdDataSource(), new MockAdRepository());

        DataResult<PageResult<AdItem>> result = repository.loadAds(PageRequest.firstPage("精选", 10));

        assertEquals(DataResult.Status.FALLBACK, result.getStatus());
        assertTrue(result.hasData());
        assertFalse(result.getData().getItems().isEmpty());
    }

    @Test
    public void interactionStateIsConsistentAcrossFeedDetailAndSearch() {
        DefaultAdRepository repository = new DefaultAdRepository(new MockAdRepository());

        AdItem liked = repository.updateInteraction("ad_001", InteractionAction.LIKE).getData();
        AdItem detail = repository.getAdById("ad_001").getData();
        AdItem search = repository.searchAds(SearchRequest.keyword("轻量跑鞋")).getData().getItems().get(0);

        assertTrue(liked.getInteractionState().isLiked());
        assertTrue(detail.getInteractionState().isLiked());
        assertTrue(search.getInteractionState().isLiked());

        repository.updateInteraction("ad_001", InteractionAction.COLLECT);
        AdItem refreshed = repository.loadAds(PageRequest.firstPage("精选", 10)).getData().getItems().get(0);
        assertTrue(refreshed.getInteractionState().isCollected());
    }

    @Test
    public void statsActionsIncrementWithoutBreakingState() {
        DefaultAdRepository repository = new DefaultAdRepository(new MockAdRepository());
        int clicks = repository.getAdById("ad_003").getData().getStats().getClickCount();

        AdItem clicked = repository.updateInteraction("ad_003", InteractionAction.CLICK).getData();
        AdItem shared = repository.updateInteraction("ad_003", InteractionAction.SHARE).getData();

        assertEquals(clicks + 1, clicked.getStats().getClickCount());
        assertEquals(clicked.getStats().getShareCount() + 1, shared.getStats().getShareCount());
    }

    @Test
    public void updateInteractionUsesRemoteWhenAvailable() {
        MockAdRepository mockRepository = new MockAdRepository();
        DefaultAdRepository repository = new DefaultAdRepository(
                new RemoteInteractionDataSource(mockRepository),
                mockRepository
        );

        DataResult<AdItem> result = repository.updateInteraction("ad_001", InteractionAction.LIKE);

        assertEquals(DataResult.Status.SUCCESS, result.getStatus());
        assertEquals("backend-test", result.getSource());
        assertTrue(result.getData().getInteractionState().isLiked());
    }

    @Test
    public void remoteInteractionFailureFallsBackToMock() {
        DefaultAdRepository repository = new DefaultAdRepository(
                new FailingRemoteAdDataSource(),
                new MockAdRepository()
        );

        DataResult<AdItem> result = repository.updateInteraction("ad_001", InteractionAction.LIKE);

        assertEquals(DataResult.Status.FALLBACK, result.getStatus());
        assertTrue(result.getData().getInteractionState().isLiked());
    }

    @Test
    public void demoRemoteUsesRemoteSourceAndKeepsInteractionStateShared() {
        MockAdRepository mockRepository = new MockAdRepository();
        DefaultAdRepository repository = new DefaultAdRepository(
                new DemoRemoteAdDataSource(mockRepository, () -> true),
                mockRepository
        );

        DataResult<PageResult<AdItem>> feed = repository.loadAds(PageRequest.firstPage("精选", 10));
        repository.updateInteraction("ad_001", InteractionAction.LIKE);
        DataResult<AdItem> detail = repository.getAdById("ad_001");

        assertEquals(DataResult.Status.SUCCESS, feed.getStatus());
        assertEquals(DemoRemoteAdDataSource.SOURCE, feed.getSource());
        assertEquals(DemoRemoteAdDataSource.SOURCE, feed.getData().getDataSource());
        assertEquals(DataResult.Status.SUCCESS, detail.getStatus());
        assertEquals(DemoRemoteAdDataSource.SOURCE, detail.getSource());
        assertTrue(detail.getData().getInteractionState().isLiked());
    }

    @Test
    public void demoRemoteFallsBackToLocalWhenOffline() {
        MockAdRepository mockRepository = new MockAdRepository();
        DefaultAdRepository repository = new DefaultAdRepository(
                new DemoRemoteAdDataSource(mockRepository, () -> false),
                mockRepository
        );

        DataResult<PageResult<AdItem>> feed = repository.loadAds(PageRequest.firstPage("精选", 10));

        assertEquals(DataResult.Status.FALLBACK, feed.getStatus());
        assertEquals(MockAdRepository.SOURCE, feed.getSource());
        assertTrue(feed.hasData());
        assertFalse(feed.getData().getItems().isEmpty());
    }

    private static final class RemoteInteractionDataSource implements RemoteAdDataSource {
        private final MockAdRepository backingRepository;

        private RemoteInteractionDataSource(MockAdRepository backingRepository) {
            this.backingRepository = backingRepository;
        }

        @Override
        public DataResult<PageResult<AdItem>> loadAds(PageRequest request) throws RemoteAdException {
            return backingRepository.loadAds(request);
        }

        @Override
        public DataResult<AdItem> getAdById(String adId) throws RemoteAdException {
            return backingRepository.getAdById(adId);
        }

        @Override
        public DataResult<PageResult<AdItem>> searchAds(SearchRequest request) throws RemoteAdException {
            return backingRepository.searchAds(request);
        }

        @Override
        public DataResult<AdItem> updateInteraction(String adId, InteractionAction action) throws RemoteAdException {
            AdItem item = backingRepository.updateInteraction(adId, action).getData();
            return DataResult.success(item, "backend-test");
        }
    }
}
