package com.nbn.adfeed.analytics.exposure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public final class ExposureTracker {
    //广告可见面积阈值
    public static final float DEFAULT_MIN_VISIBLE_RATIO = 0.5f;
    //广告曝光时间
    public static final long DEFAULT_DWELL_MILLIS = 1000L;

    private final float minVisibleRatio;
    private final long dwellMillis;
    //用于记录广告可见面积第一次超过50%的时间戳
    private final Map<String, Long> firstQualifiedAtMillis = new HashMap<>();
    //去重
    private final Set<String> exposedAdIds = new HashSet<>();

    public ExposureTracker() {
        this(DEFAULT_MIN_VISIBLE_RATIO, DEFAULT_DWELL_MILLIS);
    }

    public ExposureTracker(float minVisibleRatio, long dwellMillis) {
        this.minVisibleRatio = minVisibleRatio;
        this.dwellMillis = dwellMillis;
    }

    //每次滚动列表或视图变化时调用
    public List<String> onVisibilityChanged(Map<String, Float> visibleRatios, long nowMillis) {
        List<String> newlyExposed = new ArrayList<>();
        Set<String> qualifiedAdIds = new HashSet<>();
        //遍历可见广告先做判空+去重
        for (Map.Entry<String, Float> entry : visibleRatios.entrySet()) {
            String adId = entry.getKey();
            Float ratio = entry.getValue();
            if (adId == null || ratio == null || exposedAdIds.contains(adId)) {
                continue;
            }
            if (ratio <= minVisibleRatio) {
                continue;
            }
            //满足可见阈值的广告记录id和时间戳
            qualifiedAdIds.add(adId);
            Long firstQualifiedAt = firstQualifiedAtMillis.get(adId);
            if (firstQualifiedAt == null) {
                firstQualifiedAtMillis.put(adId, nowMillis);
                continue;
            }
            //判断当前时间-可见时间是否达指定阈值
            if (nowMillis - firstQualifiedAt >= dwellMillis) {
                //1、标记为已曝光
                exposedAdIds.add(adId);
                //2、从计时器中移除
                firstQualifiedAtMillis.remove(adId);
                //3、加入结果列表
                newlyExposed.add(adId);
            }
        }
        //从所有正在计时的广告中剔除可见阈值不满足的广告id
        firstQualifiedAtMillis.keySet().removeIf(adId -> !qualifiedAdIds.contains(adId));
        return newlyExposed;
    }

    // 通知首页多久之后复查曝光时间
    public long nextCheckDelayMillis(long nowMillis) {
        if (firstQualifiedAtMillis.isEmpty()) {
            return -1L;
        }
        
        long minDelayMillis = Long.MAX_VALUE;
        //找到所有正在计时的广告中最快达到阈值的id所需要等待的时间
        for (Long firstQualifiedAt : firstQualifiedAtMillis.values()) {
            //1、当前广告已等待时间
            long elapsedMillis = nowMillis - firstQualifiedAt;
            //2、实际需要等待时间-已等待时间
            long remainingMillis = Math.max(0L, dwellMillis - elapsedMillis);
            //3、取最快达到阈值所需要的时间进行通知
            minDelayMillis = Math.min(minDelayMillis, remainingMillis);
        }
        return minDelayMillis;
    }
}
