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
        String uri = firstHttps(primaryUri, secondaryUri);
        if (uri == null) {
            imageView.setImageResource(fallbackResId);
            return;
        }
        RequestOptions options = new RequestOptions()
                .placeholder(fallbackResId)
                .error(fallbackResId)
                .fallback(fallbackResId)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
        Glide.with(imageView)
                .load(uri)
                .apply(options)
                .into(imageView);
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
