package com.nbn.adfeed.analytics.event;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 广告统计事件的本地 SQLite 存储。
 *
 * <p>本类只负责追加事件明细，不直接计算统计结果，避免把展示口径写死在存储层。</p>
 */
public final class AdAnalyticsEventStore extends SQLiteOpenHelper {

    public AdAnalyticsEventStore(Context context) {
        // 使用 ApplicationContext，避免 Activity 销毁后 SQLiteOpenHelper 持有页面引用。
        super(context.getApplicationContext(),
                AdAnalyticsEventContract.DATABASE_NAME,
                null,
                AdAnalyticsEventContract.DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(AdAnalyticsEventContract.SQL_CREATE_TABLE);
        db.execSQL(AdAnalyticsEventContract.SQL_CREATE_INDEX_TYPE_TIME);
        db.execSQL(AdAnalyticsEventContract.SQL_CREATE_INDEX_AD_TYPE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL(AdAnalyticsEventContract.SQL_RENAME_TABLE_FOR_V2);
            db.execSQL(AdAnalyticsEventContract.SQL_CREATE_TABLE);
            db.execSQL(AdAnalyticsEventContract.SQL_COPY_V1_EVENTS_TO_V2);
            db.execSQL(AdAnalyticsEventContract.SQL_DROP_OLD_TABLE);
            db.execSQL(AdAnalyticsEventContract.SQL_CREATE_INDEX_TYPE_TIME);
            db.execSQL(AdAnalyticsEventContract.SQL_CREATE_INDEX_AD_TYPE);
        }
    }

    public long insertExposure(
            String adId,
            long occurredAtMillis,
            @Nullable Float visibleRatio,
            @Nullable Long durationMillis
    ) {
        // 曝光事件保留可见比例和停留时长，便于后续校验曝光触发质量。
        return insertEvent(
                AdAnalyticsEventContract.EVENT_EXPOSURE,
                adId,
                occurredAtMillis,
                visibleRatio,
                durationMillis,
                AdAnalyticsEventContract.SOURCE_FEED
        );
    }

    public long insertClick(String adId, long occurredAtMillis) {
        // 点击口径是“进入详情页”，不记录停留时长，停留时长由 detail_view 单独表达。
        return insertEvent(
                AdAnalyticsEventContract.EVENT_CLICK,
                adId,
                occurredAtMillis,
                null,
                null,
                AdAnalyticsEventContract.SOURCE_FEED
        );
    }

    public long insertDetailView(String adId, long occurredAtMillis, long durationMillis) {
        // 预留详情停留事件：发生时间记录进入详情的时间，duration 记录离开前停留多久。
        return insertEvent(
                AdAnalyticsEventContract.EVENT_DETAIL_VIEW,
                adId,
                occurredAtMillis,
                null,
                durationMillis,
                AdAnalyticsEventContract.SOURCE_DETAIL
        );
    }

    public long insertLike(String adId, long occurredAtMillis) {
        return insertFeedInteraction(AdAnalyticsEventContract.EVENT_LIKE, adId, occurredAtMillis);
    }

    public long insertUnlike(String adId, long occurredAtMillis) {
        return insertFeedInteraction(AdAnalyticsEventContract.EVENT_UNLIKE, adId, occurredAtMillis);
    }

    public long insertCollect(String adId, long occurredAtMillis) {
        return insertFeedInteraction(AdAnalyticsEventContract.EVENT_COLLECT, adId, occurredAtMillis);
    }

    public long insertUncollect(String adId, long occurredAtMillis) {
        return insertFeedInteraction(AdAnalyticsEventContract.EVENT_UNCOLLECT, adId, occurredAtMillis);
    }

    public long insertShare(String adId, long occurredAtMillis) {
        return insertFeedInteraction(AdAnalyticsEventContract.EVENT_SHARE, adId, occurredAtMillis);
    }

    public Map<String, AdAnalyticsEventCounts> loadCountsByAdId() {
        Map<String, AdAnalyticsEventCounts> countsByAdId = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(AdAnalyticsEventContract.SQL_SELECT_COUNTS_BY_AD, null)) {
            while (cursor.moveToNext()) {
                String adId = cursor.getString(
                        cursor.getColumnIndexOrThrow(AdAnalyticsEventContract.COLUMN_AD_ID));
                String eventType = cursor.getString(
                        cursor.getColumnIndexOrThrow(AdAnalyticsEventContract.COLUMN_EVENT_TYPE));
                int count = cursor.getInt(
                        cursor.getColumnIndexOrThrow(AdAnalyticsEventContract.COLUMN_EVENT_COUNT));

                AdAnalyticsEventCounts counts = countsByAdId.get(adId);
                if (counts == null) {
                    counts = new AdAnalyticsEventCounts();
                    countsByAdId.put(adId, counts);
                }
                if (AdAnalyticsEventContract.EVENT_EXPOSURE.equals(eventType)) {
                    counts.setExposureCount(count);
                } else if (AdAnalyticsEventContract.EVENT_CLICK.equals(eventType)) {
                    counts.setClickCount(count);
                } else if (AdAnalyticsEventContract.EVENT_LIKE.equals(eventType)) {
                    applyDelta(count, counts::increaseLikeDelta);
                } else if (AdAnalyticsEventContract.EVENT_UNLIKE.equals(eventType)) {
                    applyDelta(count, counts::decreaseLikeDelta);
                } else if (AdAnalyticsEventContract.EVENT_COLLECT.equals(eventType)) {
                    applyDelta(count, counts::increaseCollectDelta);
                } else if (AdAnalyticsEventContract.EVENT_UNCOLLECT.equals(eventType)) {
                    applyDelta(count, counts::decreaseCollectDelta);
                } else if (AdAnalyticsEventContract.EVENT_SHARE.equals(eventType)) {
                    counts.setShareCount(count);
                }
            }
        }
        return countsByAdId;
    }

    private long insertFeedInteraction(String eventType, String adId, long occurredAtMillis) {
        return insertEvent(
                eventType,
                adId,
                occurredAtMillis,
                null,
                null,
                AdAnalyticsEventContract.SOURCE_FEED
        );
    }

    private static void applyDelta(int count, Runnable operation) {
        for (int i = 0; i < count; i++) {
            operation.run();
        }
    }

    private long insertEvent(
            String eventType,
            String adId,
            long occurredAtMillis,
            @Nullable Float visibleRatio,
            @Nullable Long durationMillis,
            String source
    ) {
        if (adId == null || adId.isEmpty()) {
            throw new IllegalArgumentException("adId must not be empty");
        }

        // 统一入口保证所有事件都使用同一套字段和约束，后续扩展也更集中。
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(AdAnalyticsEventContract.COLUMN_EVENT_TYPE, eventType);
        values.put(AdAnalyticsEventContract.COLUMN_AD_ID, adId);
        values.put(AdAnalyticsEventContract.COLUMN_OCCURRED_AT, occurredAtMillis);
        values.put(AdAnalyticsEventContract.COLUMN_SOURCE, source);
        if (visibleRatio == null) {
            values.putNull(AdAnalyticsEventContract.COLUMN_VISIBLE_RATIO);
        } else {
            values.put(AdAnalyticsEventContract.COLUMN_VISIBLE_RATIO, visibleRatio);
        }
        if (durationMillis == null) {
            values.putNull(AdAnalyticsEventContract.COLUMN_DURATION_MS);
        } else {
            values.put(AdAnalyticsEventContract.COLUMN_DURATION_MS, durationMillis);
        }
        return db.insertOrThrow(AdAnalyticsEventContract.TABLE_EVENTS, null, values);
    }
}
