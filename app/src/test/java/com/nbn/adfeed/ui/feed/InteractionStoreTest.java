package com.nbn.adfeed.ui.feed;

import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionState;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class InteractionStoreTest {

    @Test
    public void applyCountsHydratesPersistedExposureAndClickCounts() {
        InteractionStore store = store();
        AdItem ad = ad("ad_001");

        store.applyCounts(ad, 4, 2);

        InteractionState state = store.stateOf(ad);
        assertEquals(4, state.getExposureCount());
        assertEquals(2, state.getClickCount());
    }

    @Test
    public void applyCountsKeepsSeededLikeAndCollectState() {
        InteractionState seed = new InteractionState();
        seed.setLiked(true);
        seed.setCollected(true);
        AdItem ad = ad("ad_001", seed);
        InteractionStore store = store();

        store.applyCounts(ad, 3, 1);

        InteractionState state = store.stateOf(ad);
        assertTrue(state.isLiked());
        assertTrue(state.isCollected());
        assertEquals(3, state.getExposureCount());
        assertEquals(1, state.getClickCount());
    }

    private static AdItem ad(String id) {
        return ad(id, new InteractionState());
    }

    private static AdItem ad(String id, InteractionState state) {
        return new AdItem(
                id,
                "测试广告",
                "NBN",
                "精选",
                "summary",
                AdContentType.LARGE_IMAGE,
                Collections.singletonList("测试"),
                state
        );
    }

    private static InteractionStore store() {
        try {
            Constructor<InteractionStore> constructor = InteractionStore.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("无法创建测试用 InteractionStore", exception);
        }
    }
}
