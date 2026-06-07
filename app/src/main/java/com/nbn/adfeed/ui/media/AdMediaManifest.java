package com.nbn.adfeed.ui.media;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 媒体清单加载器：从 {@code assets/media_manifest.json} 读取每条广告的真实
 * https 图片/视频 URL，供 {@link AdMediaLoader} 在后端返回本地路径时降级查询。
 *
 * <p>仅在首次使用时解析一次，后续 O(1) 内存查找。</p>
 */
public final class AdMediaManifest {

    private static final String ASSET_PATH = "media_manifest.json";
    private static volatile boolean loaded;
    private static final Map<String, Entry> byAdId = new HashMap<>();

    private AdMediaManifest() { }

    public static Entry of(String adId) {
        ensureLoaded(null);
        return byAdId.get(adId);
    }

    public static String imageUrl(String adId) {
        Entry e = of(adId);
        return e == null ? null : e.imageUrl;
    }

    public static String thumbnailUrl(String adId) {
        Entry e = of(adId);
        return e == null ? null : e.thumbnailUrl;
    }

    public static String videoUrl(String adId) {
        Entry e = of(adId);
        return e == null ? null : e.videoUrl;
    }

    /**
     * 预先加载清单（主线程调用一次即可）。后续 {@link #of} 无需 context。
     */
    public static synchronized void ensureLoaded(Context context) {
        if (loaded) return;
        loaded = true;
        try {
            InputStream is;
            if (context != null) {
                is = context.getAssets().open(ASSET_PATH);
            } else {
                ClassLoader cl = AdMediaManifest.class.getClassLoader();
                is = cl != null ? cl.getResourceAsStream("assets/" + ASSET_PATH) : null;
            }
            if (is == null) return;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append('\n');
                JSONArray arr = new JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String adId = obj.optString("adId", "");
                    if (adId.isEmpty()) continue;
                    byAdId.put(adId, new Entry(
                            adId,
                            nullToEmpty(obj.optString("imageUrl", null)),
                            nullToEmpty(obj.optString("thumbnailUrl", null)),
                            nullToEmpty(obj.optString("videoUrl", null)),
                            obj.optString("contentType", "LARGE_IMAGE")
                    ));
                }
            }
        } catch (Exception ignored) {
            // Manifest 不可用时降级到存量 fallback 逻辑，不阻塞 App 启动。
        }
    }

    /**
     * 广告媒体条目（图片 URL + 视频 URL）。
     */
    public static final class Entry {
        final String adId;
        final String imageUrl;
        final String thumbnailUrl;
        final String videoUrl;
        final String contentType;

        Entry(String adId, String imageUrl, String thumbnailUrl, String videoUrl, String contentType) {
            this.adId = adId;
            this.imageUrl = imageUrl;
            this.thumbnailUrl = thumbnailUrl;
            this.videoUrl = videoUrl;
            this.contentType = contentType;
        }

        boolean hasHttpsImage() {
            return AdMediaLoader.isHttpsMedia(imageUrl) || AdMediaLoader.isHttpsMedia(thumbnailUrl);
        }

        boolean hasHttpsVideo() {
            return AdMediaLoader.isHttpsMedia(videoUrl);
        }
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v.trim();
    }
}
