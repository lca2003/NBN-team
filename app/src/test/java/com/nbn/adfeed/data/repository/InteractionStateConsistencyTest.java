package com.nbn.adfeed.data.repository;

import com.nbn.adfeed.data.mock.MockAdRepository;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionAction;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.SearchRequest;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class InteractionStateConsistencyTest {
    @Test
    public void feedDetailAndSearchReadTheSameInteractionState() {
        DefaultAdRepository repository = new DefaultAdRepository(new MockAdRepository());

        repository.updateInteraction("ad_001", InteractionAction.LIKE);
        repository.updateInteraction("ad_001", InteractionAction.COLLECT);

        AdItem feed = repository.loadAds(PageRequest.firstPage("精选", 10)).getData().getItems().get(0);
        AdItem detail = repository.getAdById("ad_001").getData();
        AdItem search = repository.searchAds(SearchRequest.keyword("轻量跑鞋")).getData().getItems().get(0);

        assertTrue(feed.getInteractionState().isLiked());
        assertTrue(detail.getInteractionState().isLiked());
        assertTrue(search.getInteractionState().isLiked());
        assertTrue(feed.getInteractionState().isCollected());
        assertTrue(detail.getInteractionState().isCollected());
        assertTrue(search.getInteractionState().isCollected());
    }
}
