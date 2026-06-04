package com.nbn.adfeed.data.repository;

import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.model.SearchRequest;
import com.nbn.adfeed.data.remote.FailingRemoteAdDataSource;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FallbackRepositoryTest {
    @Test
    public void remoteFailureKeepsFeedAndSearchAvailable() {
        DefaultAdRepository repository = new DefaultAdRepository(new FailingRemoteAdDataSource(), new MockAdRepository());

        DataResult<PageResult<AdItem>> feed = repository.loadAds(PageRequest.firstPage("精选", 10));
        DataResult<PageResult<AdItem>> search = repository.searchAds(SearchRequest.keyword("咖啡"));

        assertEquals(DataResult.Status.FALLBACK, feed.getStatus());
        assertTrue(feed.hasData());
        assertFalse(feed.getData().getItems().isEmpty());
        assertEquals(DataResult.Status.FALLBACK, search.getStatus());
        assertTrue(search.hasData());
        assertFalse(search.getData().getItems().isEmpty());
    }
}
