package com.nbn.adfeed.ui.feed;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nbn.adfeed.analytics.AnalyticsTracker;
import com.nbn.adfeed.analytics.exposure.ExposureTracker;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionAction;
import com.nbn.adfeed.data.model.InteractionState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 曝光检测委托，从 FeedFragment 中提取。
 *
 * <p>职责：管理广告可见性检测、曝光记录与定时调度。</p>
 */
final class FeedExposureDelegate {

    private final ExposureTracker exposureTracker = new ExposureTracker();
    private final Runnable exposureCheckRunnable = this::checkVisibleExposures;
    /** 后台线程池：曝光 SQLite 写入移出主线程，避免滚动时掉帧。 */
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    // 外部依赖，由 Fragment 在 onViewCreated 时注入。
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private FeedAdapter adapter;
    private InteractionStore interactionStore;
    private AdCatalog adCatalog;
    private AnalyticsTracker analyticsTracker;

    /**
     * 绑定所有外部依赖。在 Fragment.onViewCreated 之后调用。
     */
    void bind(RecyclerView recyclerView,
              LinearLayoutManager layoutManager,
              FeedAdapter adapter,
              InteractionStore interactionStore,
              AdCatalog adCatalog,
              AnalyticsTracker analyticsTracker) {
        this.recyclerView = recyclerView;
        this.layoutManager = layoutManager;
        this.adapter = adapter;
        this.interactionStore = interactionStore;
        this.adCatalog = adCatalog;
        this.analyticsTracker = analyticsTracker;
    }
    /** 获取曝光检查 Runnable，供 Fragment 在加载完成后 post 执行。 */
    Runnable getExposureCheckRunnable() {
        return exposureCheckRunnable;
    }

    /** 执行一次曝光可见性检测。滚动回调和加载完成时由外部触发。 */
    void checkVisibleExposures() {
        if (recyclerView == null || layoutManager == null || adapter == null) {
            return;
        }

        Map<String, Float> visibleRatios = new HashMap<>();
        Map<String, AdItem> visibleAds = new HashMap<>();
        Map<String, Integer> visiblePositions = new HashMap<>();

        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
            for (int position = firstVisible; position <= lastVisible; position++) {
                AdItem ad = adapter.getAdAt(position);
                if (ad == null) {
                    continue;
                }
                RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
                if (holder == null) {
                    continue;
                }
                float visibleRatio = visibleRatioOf(holder.itemView);
                visibleRatios.put(ad.getId(), visibleRatio);
                visibleAds.put(ad.getId(), ad);
                visiblePositions.put(ad.getId(), position);
            }
        }

        long nowMillis = SystemClock.elapsedRealtime();
        List<String> exposedAdIds = exposureTracker.onVisibilityChanged(visibleRatios, nowMillis);
        for (String adId : exposedAdIds) {
            AdItem ad = visibleAds.get(adId);
            Integer position = visiblePositions.get(adId);
            Float visibleRatio = visibleRatios.get(adId);
            if (ad != null && position != null && visibleRatio != null) {
                recordExposure(ad, position, visibleRatio);
            }
        }
        scheduleNextExposureCheck(nowMillis);
    }

    /** 页面暂停时停止曝光调度。 */
    void onPause() {
        if (recyclerView != null) {
            recyclerView.removeCallbacks(exposureCheckRunnable);
        }
        exposureTracker.onVisibilityChanged(new HashMap<>(), SystemClock.elapsedRealtime());
    }

    /** 视图销毁时清理回调和后台线程池。 */
    void onDestroyView() {
        if (recyclerView != null) {
            recyclerView.removeCallbacks(exposureCheckRunnable);
        }
        backgroundExecutor.shutdown();
    }

    // ---- 内部方法 ----

    private float visibleRatioOf(View itemView) {
        int width = itemView.getWidth();
        int height = itemView.getHeight();
        if (width <= 0 || height <= 0) {
            return 0f;
        }
        Rect visibleRect = new Rect();
        if (!itemView.getLocalVisibleRect(visibleRect)) {
            return 0f;
        }
        int visibleArea = visibleRect.width() * visibleRect.height();
        int totalArea = width * height;
        if (totalArea <= 0) {
            return 0f;
        }
        return Math.min(1f, (float) visibleArea / (float) totalArea);
    }

    private void recordExposure(AdItem ad, int position, float visibleRatio) {
        // 内存状态更新 & adapter 刷新在主线程即时完成。
        InteractionState state = interactionStore.stateOf(ad);
        state.increaseExposureCount();
        adCatalog.updateInteraction(ad.getId(), InteractionAction.EXPOSE);
        adapter.notifyItemChanged(position);

        // SQLite 持久化移到后台线程，避免 scroll 期间主线程 getWritableDatabase() 导致的掉帧。
        final String adId = ad.getId();
        backgroundExecutor.execute(() -> {
            try {
                analyticsTracker.trackExposure(adId, visibleRatio, ExposureTracker.DEFAULT_DWELL_MILLIS);
            } catch (Exception ignored) { }
        });
    }

    private void scheduleNextExposureCheck(long nowMillis) {
        recyclerView.removeCallbacks(exposureCheckRunnable);
        long delayMillis = exposureTracker.nextCheckDelayMillis(nowMillis);
        if (delayMillis >= 0L) {
            recyclerView.postDelayed(exposureCheckRunnable, delayMillis);
        }
    }
}
