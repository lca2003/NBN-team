package com.nbn.adfeed.ui.media;

import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.nbn.adfeed.data.model.AdItem;

public final class AdMediaLoader {
    private AdMediaLoader() {
    }

    public static void loadFeedImage(ImageView imageView, AdItem item) {
        if (item == null) {
            load(imageView, null, null, AdMediaResources.fallbackDrawable((AdItem) null));
            return;
        }
        load(imageView, item.getThumbnailUrl(), item.getImageUrl(), AdMediaResources.fallbackDrawable(item));
    }

    public static void loadDetailImage(ImageView imageView, AdItem item) {
        if (item == null) {
            load(imageView, null, null, AdMediaResources.fallbackDrawable((AdItem) null));
            return;
        }
        load(imageView, item.getImageUrl(), item.getThumbnailUrl(), AdMediaResources.fallbackDrawable(item));
    }

    public static void loadSearchThumbnail(ImageView imageView, String thumbnailUrl, String imageUrl, int fallbackResId) {
        load(imageView, thumbnailUrl, imageUrl, fallbackResId);
    }

    static void load(ImageView imageView, String primaryUri, String secondaryUri, int fallbackResId) {
        if (imageView == null) {
            return;
        }

        // 1) 优先用 https:// URL 通过 Glide 加载。
        String uri = firstHttps(primaryUri, secondaryUri);
        if (uri != null) {
            int targetW = imageView.getWidth();
            int targetH = imageView.getHeight();
            // 未布局时用屏幕宽度兜底，避免全分辨率解码（4000px → ~1080px，Bitmap 节省 ~14x 内存）。
            if (targetW <= 0 || targetH <= 0) {
                targetW = imageView.getResources().getDisplayMetrics().widthPixels;
                targetH = targetW;
            }
            RequestOptions options = new RequestOptions()
                    .placeholder(fallbackResId)
                    .error(fallbackResId)
                    .fallback(fallbackResId)
                    .override(targetW, targetH)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
            Glide.with(imageView)
                    .load(uri)
                    .apply(options)
                    .into(imageView);
            return;
        }

        // 2) 非 https 时尝试解析 android.resource:// URI（Mock 数据场景），
        //    直接映射到对应的本地 drawable，保证详情页与信息流图片一致。
        int resourceId = AdMediaResources.drawableFromUri(primaryUri);
        if (resourceId == 0) {
            resourceId = AdMediaResources.drawableFromUri(secondaryUri);
        }
        if (resourceId != 0) {
            imageView.setImageResource(resourceId);
            return;
        }

        // 3) 实在没有可用 URI：展示主题 fallback。
        imageView.setImageResource(fallbackResId);
    }

    public static boolean isHttpsMedia(String uri) {
        return uri != null && uri.trim().startsWith("https://");
    }

    private static String firstHttps(String firstUri, String secondUri) {
        if (isHttpsMedia(firstUri)) {
            return firstUri.trim();
        }
        if (isHttpsMedia(secondUri)) {
            return secondUri.trim();
        }
        return null;
    }
}
