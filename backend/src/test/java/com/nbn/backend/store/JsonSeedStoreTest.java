package com.nbn.backend.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.nbn.backend.BackendServer;
import org.junit.Test;

public final class JsonSeedStoreTest {
    @Test
    public void loadsAllExpectedSeedFilesFromBackendResources() throws Exception {
        JsonSeedStore store = JsonSeedStore.loadDefault(BackendServer.class.getClassLoader());

        assertEquals(7, store.seedStatus().expectedCount());
        assertEquals(7, store.seedStatus().loadedCount());
        assertEquals("loaded", store.seedStatus().status());
        assertTrue(store.rawJson("home_feed.json").contains("\"channels\""));
        assertTrue(store.rawJson("profile.json").contains("\"userProfile\""));
    }

    @Test
    public void exposesDomainCountsForSixStitchDomains() throws Exception {
        JsonSeedStore store = JsonSeedStore.loadDefault(BackendServer.class.getClassLoader());

        assertEquals(3, store.feedChannelCount());
        assertEquals(5, store.feedItemCount());
        assertEquals(5, store.detailCount());
        assertEquals(6, store.searchSuggestionCount());
        assertEquals(4, store.searchResultCount());
        assertEquals(5, store.notificationCount());
        assertEquals(4, store.conversationCount());
        assertEquals(5, store.profilePostCount());
        assertEquals(5, store.reviewGroupCount());
        assertEquals(6, store.commentCount());
        assertEquals(6, store.assetCount());
    }

    @Test
    public void summaryJsonIncludesBackendReadyCounters() throws Exception {
        JsonSeedStore store = JsonSeedStore.loadDefault(BackendServer.class.getClassLoader());

        String summary = store.domainSummaryJson();

        assertTrue(summary.contains("\"feedItems\":5"));
        assertTrue(summary.contains("\"adDetails\":5"));
        assertTrue(summary.contains("\"searchResults\":4"));
        assertTrue(summary.contains("\"profilePosts\":5"));
        assertTrue(summary.contains("\"reviewGroups\":5"));
        assertTrue(summary.contains("\"comments\":6"));
        assertTrue(summary.contains("\"assets\":6"));
    }
}
