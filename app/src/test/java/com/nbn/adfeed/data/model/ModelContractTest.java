package com.nbn.adfeed.data.model;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ModelContractTest {
    @Test
    public void adItemCoversFeedDetailSearchStatsAndAiFields() {
        AdItem item = new AdItem(
                "ad_test",
                "标题",
                "品牌",
                "精选",
                "featured",
                "描述",
                "摘要",
                "image",
                "thumb",
                "video",
                "¥199 · 学生价",
                "查看商品",
                AdContentType.VIDEO,
                Arrays.asList("标签1", "标签2", "标签3"),
                new InteractionState(true, false),
                new AdStats(1, 2, 3, 4, 5),
                "hash"
        );

        assertEquals("ad_test", item.getId());
        assertEquals("featured", item.getChannelId());
        assertEquals("描述", item.getDescription());
        assertEquals("摘要", item.getSummary());
        assertEquals("video", item.getVideoUrl());
        assertEquals("¥199 · 学生价", item.getOfferText());
        assertEquals("查看商品", item.getCtaText());
        assertTrue(item.getInteractionState().isLiked());
        assertEquals(5, item.getStats().getShareCount());
        assertEquals("hash", item.getContentHash());
    }

    @Test
    public void pageRequestAndPageResultExpressPagingSemantics() {
        PageRequest first = PageRequest.firstPage("精选", 10);
        PageResult<String> empty = PageResult.empty(first, "mock");

        assertTrue(first.isRefresh());
        assertEquals(1, first.getPageNumber());
        assertTrue(empty.isEmpty());
        assertFalse(empty.hasMore());
        assertEquals("mock", empty.getDataSource());
    }

    @Test
    public void dataResultExpressesAllRequiredStates() {
        assertEquals(DataResult.Status.SUCCESS, DataResult.success("ok", "mock").getStatus());
        assertEquals(DataResult.Status.EMPTY, DataResult.empty(null, "mock", "empty").getStatus());
        assertEquals(DataResult.Status.TIMEOUT, DataResult.timeout("remote", "timeout", null).getStatus());
        assertEquals(DataResult.Status.PARSE_ERROR, DataResult.parseError("mock", "parse", null).getStatus());
        assertEquals(DataResult.Status.REMOTE_ERROR, DataResult.remoteError("remote", "error", null).getStatus());
        assertEquals(DataResult.Status.FALLBACK, DataResult.fallback("ok", "mock", "fallback", null).getStatus());
    }
}
