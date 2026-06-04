package com.nbn.adfeed.ui.media;

import com.nbn.adfeed.R;
import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;

public final class AdMediaResources {
    private static final String DRAWABLE_PREFIX = "android.resource://com.nbn.adfeed/drawable/";
    private static final String RAW_PREFIX = "android.resource://com.nbn.adfeed/raw/";

    private AdMediaResources() {
    }

    public static int drawableFromUri(String rawUri) {
        if (rawUri == null || !rawUri.startsWith(DRAWABLE_PREFIX)) {
            return 0;
        }
        return drawableByName(rawUri.substring(DRAWABLE_PREFIX.length()));
    }

    public static int rawFromUri(String rawUri) {
        if (rawUri == null || !rawUri.startsWith(RAW_PREFIX)) {
            return 0;
        }
        return rawByName(rawUri.substring(RAW_PREFIX.length()));
    }

    public static String rawResourceUri(String rawUri) {
        int resourceId = rawFromUri(rawUri);
        return resourceId == 0 ? rawUri : "rawresource:///" + resourceId;
    }

    public static int fallbackDrawable(AdItem item) {
        if (item == null) {
            return R.drawable.ad_media_large_market;
        }
        int themed = drawableByTheme(item.getAssetTheme(), item.getContentType());
        if (themed != 0) {
            return themed;
        }
        if (item.getContentType() == AdContentType.VIDEO) {
            if (containsAny(item, "学习", "AI", "摄影")) {
                return R.drawable.ad_media_video_study;
            }
            if (containsAny(item, "本地", "篮球", "夜跑", "运动")) {
                return R.drawable.ad_media_video_local_sports;
            }
            return R.drawable.ad_media_video_headphones;
        }
        if (item.getContentType() == AdContentType.SMALL_IMAGE) {
            if (containsAny(item, "咖啡", "早餐", "午餐")) {
                return R.drawable.ad_media_small_cafe;
            }
            if (containsAny(item, "健身", "运动", "健康")) {
                return R.drawable.ad_media_small_fitness;
            }
            if (containsAny(item, "数码", "效率", "键盘")) {
                return R.drawable.ad_media_small_tech;
            }
            return R.drawable.ad_media_small_lifestyle;
        }
        if (containsAny(item, "学习", "校园", "AI")) {
            return R.drawable.ad_media_large_study;
        }
        if (containsAny(item, "数码", "通勤", "装备")) {
            return R.drawable.ad_media_large_tech;
        }
        if (containsAny(item, "咖啡", "市集", "周末")) {
            return R.drawable.ad_media_large_market;
        }
        if (containsAny(item, "户外", "露营", "骑行")) {
            return R.drawable.ad_media_large_outdoor;
        }
        return R.drawable.ad_media_large_sports;
    }

    public static int fallbackDrawable(AdContentType contentType) {
        if (contentType == AdContentType.VIDEO) {
            return R.drawable.ad_media_video_headphones;
        }
        if (contentType == AdContentType.SMALL_IMAGE) {
            return R.drawable.ad_media_small_lifestyle;
        }
        return R.drawable.ad_media_large_market;
    }

    private static int drawableByTheme(String theme, AdContentType contentType) {
        String normalized = theme == null ? "" : theme.trim();
        if ("commerce".equals(normalized)) {
            return contentType == AdContentType.SMALL_IMAGE
                    ? R.drawable.ad_media_small_tech
                    : R.drawable.ad_media_large_tech;
        }
        if ("local".equals(normalized)) {
            return contentType == AdContentType.VIDEO
                    ? R.drawable.ad_media_video_local_sports
                    : R.drawable.ad_media_small_cafe;
        }
        if ("video".equals(normalized)) {
            return R.drawable.ad_media_video_headphones;
        }
        if ("featured".equals(normalized)) {
            return R.drawable.ad_media_large_sports;
        }
        return 0;
    }

    private static int drawableByName(String name) {
        switch (name == null ? "" : name.trim()) {
            case "ad_media_large_market":
                return R.drawable.ad_media_large_market;
            case "ad_media_large_outdoor":
                return R.drawable.ad_media_large_outdoor;
            case "ad_media_large_sports":
                return R.drawable.ad_media_large_sports;
            case "ad_media_large_study":
                return R.drawable.ad_media_large_study;
            case "ad_media_large_tech":
                return R.drawable.ad_media_large_tech;
            case "ad_media_small_cafe":
                return R.drawable.ad_media_small_cafe;
            case "ad_media_small_fitness":
                return R.drawable.ad_media_small_fitness;
            case "ad_media_small_lifestyle":
                return R.drawable.ad_media_small_lifestyle;
            case "ad_media_small_tech":
                return R.drawable.ad_media_small_tech;
            case "ad_media_video_headphones":
                return R.drawable.ad_media_video_headphones;
            case "ad_media_video_local_sports":
                return R.drawable.ad_media_video_local_sports;
            case "ad_media_video_study":
                return R.drawable.ad_media_video_study;
            default:
                return 0;
        }
    }

    private static int rawByName(String name) {
        switch (name == null ? "" : name.trim()) {
            case "ad_video_headphones":
                return R.raw.ad_video_headphones;
            case "ad_video_local_sports":
                return R.raw.ad_video_local_sports;
            case "ad_video_study_ai":
                return R.raw.ad_video_study_ai;
            default:
                return 0;
        }
    }

    private static boolean containsAny(AdItem item, String... needles) {
        String haystack = (item.getTitle() + " " + item.getBrand() + " "
                + item.getChannel() + " " + String.join(" ", item.getTags())).toLowerCase(java.util.Locale.ROOT);
        for (String needle : needles) {
            if (haystack.contains(needle.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
