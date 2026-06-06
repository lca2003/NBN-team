package com.nbn.adfeed.analytics.event;

/**
 * 广告统计事件表的 SQLite 契约。
 *
 * <p>当前只做本地持久化，表中统一保存曝光、点击和预留的详情停留事件。</p>
 */
final class AdAnalyticsEventContract {
    static final String DATABASE_NAME = "ad_analytics_events.db";
    static final int DATABASE_VERSION = 2;

    static final String TABLE_EVENTS = "ad_analytics_events";
    static final String TABLE_EVENTS_OLD = "ad_analytics_events_old";

    // 表字段：事件明细以追加写入为主，统计页后续通过 GROUP BY 汇总。
    static final String COLUMN_ID = "id";
    static final String COLUMN_EVENT_TYPE = "event_type";
    static final String COLUMN_AD_ID = "ad_id";
    static final String COLUMN_OCCURRED_AT = "occurred_at";
    static final String COLUMN_VISIBLE_RATIO = "visible_ratio";
    static final String COLUMN_DURATION_MS = "duration_ms";
    static final String COLUMN_SOURCE = "source";
    static final String COLUMN_EVENT_COUNT = "event_count";

    // 事件类型：detail_view 先预留，后续详情页接入停留时长时直接复用。
    static final String EVENT_EXPOSURE = "exposure";
    static final String EVENT_CLICK = "click";
    static final String EVENT_DETAIL_VIEW = "detail_view";
    static final String EVENT_LIKE = "like";
    static final String EVENT_UNLIKE = "unlike";
    static final String EVENT_COLLECT = "collect";
    static final String EVENT_UNCOLLECT = "uncollect";
    static final String EVENT_SHARE = "share";

    static final String SOURCE_FEED = "feed";
    static final String SOURCE_DETAIL = "detail";

    static final String EVENT_TYPE_CHECK = "'" + EVENT_EXPOSURE + "', '" + EVENT_CLICK + "', '"
            + EVENT_DETAIL_VIEW + "', '" + EVENT_LIKE + "', '" + EVENT_UNLIKE + "', '"
            + EVENT_COLLECT + "', '" + EVENT_UNCOLLECT + "', '" + EVENT_SHARE + "'";

    static final String SQL_CREATE_TABLE = "CREATE TABLE " + TABLE_EVENTS + " ("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COLUMN_EVENT_TYPE + " TEXT NOT NULL CHECK (" + COLUMN_EVENT_TYPE
            + " IN (" + EVENT_TYPE_CHECK + ")), "
            + COLUMN_AD_ID + " TEXT NOT NULL, "
            + COLUMN_OCCURRED_AT + " INTEGER NOT NULL, "
            + COLUMN_VISIBLE_RATIO + " REAL, "
            + COLUMN_DURATION_MS + " INTEGER, "
            + COLUMN_SOURCE + " TEXT NOT NULL DEFAULT '" + SOURCE_FEED + "'"
            + ")";

    static final String SQL_RENAME_TABLE_FOR_V2 = "ALTER TABLE " + TABLE_EVENTS
            + " RENAME TO " + TABLE_EVENTS_OLD;

    static final String SQL_COPY_V1_EVENTS_TO_V2 = "INSERT INTO " + TABLE_EVENTS + " ("
            + COLUMN_ID + ", " + COLUMN_EVENT_TYPE + ", " + COLUMN_AD_ID + ", "
            + COLUMN_OCCURRED_AT + ", " + COLUMN_VISIBLE_RATIO + ", " + COLUMN_DURATION_MS + ", "
            + COLUMN_SOURCE + ") SELECT "
            + COLUMN_ID + ", " + COLUMN_EVENT_TYPE + ", " + COLUMN_AD_ID + ", "
            + COLUMN_OCCURRED_AT + ", " + COLUMN_VISIBLE_RATIO + ", " + COLUMN_DURATION_MS + ", "
            + COLUMN_SOURCE + " FROM " + TABLE_EVENTS_OLD;

    static final String SQL_DROP_OLD_TABLE = "DROP TABLE IF EXISTS " + TABLE_EVENTS_OLD;

    // 支持按事件类型和时间段查询，例如统计最近一段时间的曝光或点击。
    static final String SQL_CREATE_INDEX_TYPE_TIME = "CREATE INDEX idx_ad_events_type_time ON "
            + TABLE_EVENTS + "(" + COLUMN_EVENT_TYPE + ", " + COLUMN_OCCURRED_AT + ")";

    // 支持按广告维度汇总曝光/点击次数，是统计页最常用的查询路径。
    static final String SQL_CREATE_INDEX_AD_TYPE = "CREATE INDEX idx_ad_events_ad_type ON "
            + TABLE_EVENTS + "(" + COLUMN_AD_ID + ", " + COLUMN_EVENT_TYPE + ")";

    static final String SQL_SELECT_COUNTS_BY_AD = "SELECT "
            + COLUMN_AD_ID + ", "
            + COLUMN_EVENT_TYPE + ", "
            + "COUNT(*) AS " + COLUMN_EVENT_COUNT
            + " FROM " + TABLE_EVENTS
            + " WHERE " + COLUMN_EVENT_TYPE + " IN (" + EVENT_TYPE_CHECK + ")"
            + " GROUP BY " + COLUMN_AD_ID + ", " + COLUMN_EVENT_TYPE;

    private AdAnalyticsEventContract() {
    }
}
