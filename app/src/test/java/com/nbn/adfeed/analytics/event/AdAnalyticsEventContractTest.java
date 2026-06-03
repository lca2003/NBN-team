package com.nbn.adfeed.analytics.event;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * 锁定事件表的最小结构，防止后续误删点击、曝光或预留的详情停留字段。
 */
public final class AdAnalyticsEventContractTest {

    @Test
    public void createTableSqlDefinesExposureClickAndDetailViewEvents() {
        String createSql = AdAnalyticsEventContract.SQL_CREATE_TABLE;

        assertTrue(createSql.contains("ad_analytics_events"));
        assertTrue(createSql.contains("event_type IN ('exposure', 'click', 'detail_view')"));
        assertTrue(createSql.contains("visible_ratio REAL"));
        assertTrue(createSql.contains("duration_ms INTEGER"));
        assertTrue(createSql.contains("source TEXT NOT NULL DEFAULT 'feed'"));
    }

    @Test
    public void indexSqlSupportsStatsQueriesByTypeTimeAndAdType() {
        assertTrue(AdAnalyticsEventContract.SQL_CREATE_INDEX_TYPE_TIME
                .contains("(event_type, occurred_at)"));
        assertTrue(AdAnalyticsEventContract.SQL_CREATE_INDEX_AD_TYPE
                .contains("(ad_id, event_type)"));
    }

    @Test
    public void selectCountsSqlGroupsExposureAndClickByAdIdAndType() {
        String querySql = AdAnalyticsEventContract.SQL_SELECT_COUNTS_BY_AD;

        assertTrue(querySql.contains("ad_id"));
        assertTrue(querySql.contains("event_type"));
        assertTrue(querySql.contains("COUNT(*)"));
        assertTrue(querySql.contains("event_type IN ('exposure', 'click')"));
        assertTrue(querySql.contains("GROUP BY ad_id, event_type"));
    }
}
