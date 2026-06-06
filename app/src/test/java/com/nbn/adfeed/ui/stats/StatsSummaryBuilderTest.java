package com.nbn.adfeed.ui.stats;

import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.AdStats;
import com.nbn.adfeed.data.model.InteractionState;
import com.nbn.adfeed.ui.feed.InteractionStore;
import com.nbn.adfeed.analytics.event.AdAnalyticsEventCounts;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public final class StatsSummaryBuilderTest {

    @Test
    public void buildsTotalsClickRateAndContentTypeDistribution() {
        List<AdItem> ads = Arrays.asList(
                ad("ad_001", "大型图", AdContentType.LARGE_IMAGE, 3, 1, "数码"),
                ad("ad_002", "小图", AdContentType.SMALL_IMAGE, 1, 0, "健康"),
                ad("ad_003", "视频", AdContentType.VIDEO, 2, 1, "生活")
        );

        StatsSummary summary = StatsSummaryBuilder.fromAds(ads, store());

        assertEquals(6, summary.getTotalExposureCount());
        assertEquals(2, summary.getTotalClickCount());
        assertEquals(33, summary.getClickRatePercent());
        assertEquals(3, summary.getTotalAdCount());
        assertEquals(1, summary.getContentTypeCount(AdContentType.LARGE_IMAGE));
        assertEquals(1, summary.getContentTypeCount(AdContentType.SMALL_IMAGE));
        assertEquals(1, summary.getContentTypeCount(AdContentType.VIDEO));
        assertEquals(33, summary.getContentTypePercent(AdContentType.LARGE_IMAGE));
    }

    @Test
    public void buildsInteractionTotalsFromPersistentEventDeltasWithoutDoubleCountingBaseStats() {
        List<AdItem> ads = Arrays.asList(
                adWithStats("ad_001", "第一条", AdContentType.LARGE_IMAGE,
                        new AdStats(4, 2, 10, 6, 3), "数码"),
                adWithStats("ad_002", "第二条", AdContentType.SMALL_IMAGE,
                        new AdStats(2, 1, 5, 4, 1), "生活")
        );
        InteractionStore interactionStore = store();
        interactionStore.stateOf(ads.get(0)).setExposureCount(4);
        interactionStore.stateOf(ads.get(0)).setClickCount(2);
        interactionStore.stateOf(ads.get(1)).setExposureCount(2);
        interactionStore.stateOf(ads.get(1)).setClickCount(1);
        Map<String, AdAnalyticsEventCounts> eventCounts = new java.util.HashMap<>();
        eventCounts.put("ad_001", eventCounts(2, -1, 4));
        eventCounts.put("ad_002", eventCounts(-1, 3, 2));

        StatsSummary summary = StatsSummaryBuilder.fromAds(ads, interactionStore, eventCounts);

        assertEquals(2, summary.getTotalLikeCount());
        assertEquals(3, summary.getTotalCollectCount());
        assertEquals(6, summary.getTotalShareCount());
        assertEquals(183, summary.getInteractionRatePercent());
        assertEquals(2, summary.getTopAds().get(0).getLikeCount());
        assertEquals(0, summary.getTopAds().get(0).getCollectCount());
        assertEquals(4, summary.getTopAds().get(0).getShareCount());
    }

    @Test
    public void contentTypeCountReturnsZeroWhenTypeIsMissing() {
        Map<AdContentType, Integer> contentTypeCounts = new EnumMap<>(AdContentType.class);
        contentTypeCounts.put(AdContentType.LARGE_IMAGE, 1);
        StatsSummary summary = new StatsSummary(
                0,
                0,
                0,
                0,
                0,
                1,
                contentTypeCounts,
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertEquals(1, summary.getContentTypeCount(AdContentType.LARGE_IMAGE));
        assertEquals(0, summary.getContentTypeCount(AdContentType.SMALL_IMAGE));
        assertEquals(0, summary.getContentTypeCount(AdContentType.VIDEO));
        assertEquals(0, summary.getContentTypePercent(AdContentType.SMALL_IMAGE));
    }

    @Test
    public void sortsTopAdsByExposureThenClickAndLimitsToFive() {
        List<AdItem> ads = Arrays.asList(
                ad("ad_001", "第一条", AdContentType.LARGE_IMAGE, 7, 1, "通勤"),
                ad("ad_002", "第二条", AdContentType.SMALL_IMAGE, 10, 2, "数码"),
                ad("ad_003", "第三条", AdContentType.VIDEO, 10, 3, "数码"),
                ad("ad_004", "第四条", AdContentType.LARGE_IMAGE, 10, 2, "生活"),
                ad("ad_005", "第五条", AdContentType.SMALL_IMAGE, 6, 5, "健康"),
                ad("ad_006", "第六条", AdContentType.VIDEO, 5, 5, "运动")
        );

        StatsSummary summary = StatsSummaryBuilder.fromAds(ads, store());

        assertEquals(5, summary.getTopAds().size());
        assertEquals("ad_003", summary.getTopAds().get(0).getAdId());
        assertEquals("ad_002", summary.getTopAds().get(1).getAdId());
        assertEquals("ad_004", summary.getTopAds().get(2).getAdId());
    }

    @Test
    public void sortsTagHeatByCountThenNameAndLimitsToSix() {
        List<AdItem> ads = Arrays.asList(
                ad("ad_001", "第一条", AdContentType.LARGE_IMAGE, 1, 0, "数码", "健康", "gamma"),
                ad("ad_002", "第二条", AdContentType.SMALL_IMAGE, 1, 0, "数码", "健康", "beta"),
                ad("ad_003", "第三条", AdContentType.VIDEO, 1, 0, "数码", "alpha", "delta"),
                ad("ad_004", "第四条", AdContentType.LARGE_IMAGE, 1, 0, "epsilon", "zeta")
        );

        StatsSummary summary = StatsSummaryBuilder.fromAds(ads, store());

        assertEquals(6, summary.getTagHeat().size());
        assertEquals("数码", summary.getTagHeat().get(0).getName());
        assertEquals(3, summary.getTagHeat().get(0).getCount());
        assertEquals("健康", summary.getTagHeat().get(1).getName());
        assertEquals(2, summary.getTagHeat().get(1).getCount());
        assertEquals("alpha", summary.getTagHeat().get(2).getName());
        assertEquals("beta", summary.getTagHeat().get(3).getName());
        assertEquals("delta", summary.getTagHeat().get(4).getName());
        assertEquals("epsilon", summary.getTagHeat().get(5).getName());
    }

    private static AdItem ad(
            String id,
            String title,
            AdContentType contentType,
            int exposureCount,
            int clickCount,
            String... tags
    ) {
        InteractionState state = new InteractionState();
        for (int i = 0; i < exposureCount; i++) {
            state.increaseExposureCount();
        }
        for (int i = 0; i < clickCount; i++) {
            state.increaseClickCount();
        }
        return new AdItem(
                id,
                title,
                "NBN",
                "精选",
                "summary",
                contentType,
                Arrays.asList(tags),
                state
        );
    }

    private static AdItem adWithStats(
            String id,
            String title,
            AdContentType contentType,
            AdStats stats,
            String... tags
    ) {
        return new AdItem(
                id,
                title,
                "NBN",
                "精选",
                "精选",
                "description",
                "summary",
                null,
                null,
                null,
                contentType,
                Arrays.asList(tags),
                new InteractionState(),
                stats,
                null
        );
    }

    private static AdAnalyticsEventCounts eventCounts(int likeDelta, int collectDelta, int shareCount) {
        return new AdAnalyticsEventCounts(0, 0, likeDelta, collectDelta, shareCount);
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
