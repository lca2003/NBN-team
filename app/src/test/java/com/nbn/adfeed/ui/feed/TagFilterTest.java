package com.nbn.adfeed.ui.feed;

import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionState;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public final class TagFilterTest {

    @Test
    public void emptyTagReturnsCopyOfAllItems() {
        List<AdItem> ads = Arrays.asList(
                ad("ad_001", "运动", "学生党"),
                ad("ad_002", "本地", "休闲")
        );

        List<AdItem> result = TagFilter.byTag(ads, "");

        assertEquals(ads, result);
        assertNotSame(ads, result);
    }

    @Test
    public void filtersByExactTagAndKeepsOrder() {
        AdItem first = ad("ad_001", "运动", "学生党");
        AdItem second = ad("ad_002", "本地", "休闲");
        AdItem third = ad("ad_003", "运动", "通勤");

        List<AdItem> result = TagFilter.byTag(Arrays.asList(first, second, third), "运动");

        assertEquals(Arrays.asList(first, third), result);
    }

    @Test
    public void doesNotMatchTitleOrSummaryWhenTagIsMissing() {
        AdItem ad = new AdItem(
                "ad_001",
                "运动耳机",
                "NBN",
                "精选",
                "适合运动场景",
                AdContentType.LARGE_IMAGE,
                Arrays.asList("数码", "通勤"),
                new InteractionState()
        );

        assertEquals(0, TagFilter.byTag(Arrays.asList(ad), "运动").size());
    }

    private static AdItem ad(String id, String... tags) {
        return new AdItem(
                id,
                "title " + id,
                "brand",
                "精选",
                "summary",
                AdContentType.LARGE_IMAGE,
                Arrays.asList(tags),
                new InteractionState()
        );
    }
}
