package com.nbn.adfeed.analytics;

import android.content.Context;

import com.nbn.adfeed.analytics.event.AdAnalyticsEventCounts;
import com.nbn.adfeed.analytics.event.AdAnalyticsEventStore;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 统计上报入口。
 *
 * <p>保留原来的内存计数，带 Context 构造时额外把广告事件写入 SQLite。</p>
 */
public final class AnalyticsTracker {
    // 内存计数用于兼容现有调用和测试，不依赖本地数据库是否可用。
    private final Map<String, Integer> counters = new HashMap<>();
    // 允许为空：无 Context 的测试或旧调用只使用内存统计，不做持久化。
    private final AdAnalyticsEventStore eventStore;

    public AnalyticsTracker() {
        this.eventStore = null;
    }

    public AnalyticsTracker(Context context) {
        // SQLiteOpenHelper 需要 Context，因此在 Activity/Fragment 生命周期内创建带存储能力的 tracker。
        this.eventStore = new AdAnalyticsEventStore(context);
    }

    public void trackAppOpen() {
        increase("app_open");
    }

    public void trackExposure(String adId) {
        trackExposure(adId, null, null);
    }

    public void trackExposure(String adId, Float visibleRatio, Long durationMillis) {
        increase("exposure_" + adId);
        persist(() -> {
            if (eventStore != null) {
                // 发生时间使用系统墙钟时间，方便后续按日期筛选统计事件。
                eventStore.insertExposure(adId, System.currentTimeMillis(), visibleRatio, durationMillis);
            }
        });
    }

    public void trackClick(String adId) {
        increase("click_" + adId);
        persist(() -> {
            if (eventStore != null) {
                // 点击只表示进入详情页，不混入详情页停留时长。
                eventStore.insertClick(adId, System.currentTimeMillis());
            }
        });
    }

    public void trackLike(String adId) {
        increase("like_" + adId);
        persist(() -> {
            if (eventStore != null) {
                eventStore.insertLike(adId, System.currentTimeMillis());
            }
        });
    }

    public void trackUnlike(String adId) {
        increase("unlike_" + adId);
        persist(() -> {
            if (eventStore != null) {
                eventStore.insertUnlike(adId, System.currentTimeMillis());
            }
        });
    }

    public void trackCollect(String adId) {
        increase("collect_" + adId);
        persist(() -> {
            if (eventStore != null) {
                eventStore.insertCollect(adId, System.currentTimeMillis());
            }
        });
    }

    public void trackUncollect(String adId) {
        increase("uncollect_" + adId);
        persist(() -> {
            if (eventStore != null) {
                eventStore.insertUncollect(adId, System.currentTimeMillis());
            }
        });
    }

    public void trackShare(String adId) {
        increase("share_" + adId);
        persist(() -> {
            if (eventStore != null) {
                eventStore.insertShare(adId, System.currentTimeMillis());
            }
        });
    }

    public void trackDetailView(String adId, long startedAtMillis, long durationMillis) {
        increase("detail_view_" + adId);
        persist(() -> {
            if (eventStore != null) {
                // detail_view 预留给详情页离开时调用，startedAtMillis 表示进入详情的时间。
                eventStore.insertDetailView(adId, startedAtMillis, durationMillis);
            }
        });
    }

    public Map<String, Integer> snapshot() {
        return new HashMap<>(counters);
    }

    public Map<String, AdAnalyticsEventCounts> loadCountsByAdId() {
        if (eventStore == null) {
            return Collections.emptyMap();
        }
        try {
            return eventStore.loadCountsByAdId();
        } catch (RuntimeException ignored) {
            return Collections.emptyMap();
        }
    }

    private void increase(String key) {
        Integer current = counters.get(key);
        counters.put(key, current == null ? 1 : current + 1);
    }

    private static void persist(Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException ignored) {
            // 统计落库失败不影响页面主流程，内存计数仍保留本次事件。
        }
    }
}
