package com.nbn.adfeed.data.model;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class AdModelContractTest {
    @Test
    public void adModelHasFieldsForFeedDetailSearchStatsAndAi() {
        AdItem item = new AdItem(
                "ad_model",
                "学生党通勤数码包",
                "Cable Box",
                "电商",
                "commerce",
                "适合宿舍、图书馆和通勤。",
                "数码配件包，适合学生通勤。",
                "image",
                "thumb",
                null,
                "¥89 · 学生套装价",
                "查看商品",
                AdContentType.LARGE_IMAGE,
                Arrays.asList("学生党", "数码", "通勤"),
                new InteractionState(false, true),
                new AdStats(10, 2, 3, 4, 1),
                "hash_model"
        );

        assertEquals("commerce", item.getChannelId());
        assertEquals("适合宿舍、图书馆和通勤。", item.getDescription());
        assertEquals("数码配件包，适合学生通勤。", item.getSummary());
        assertEquals("¥89 · 学生套装价", item.getOfferText());
        assertEquals("查看商品", item.getCtaText());
        assertEquals(3, item.getTags().size());
        assertTrue(item.getInteractionState().isCollected());
        assertEquals(10, item.getStats().getExposureCount());
        assertEquals("hash_model", item.getContentHash());
    }
}
