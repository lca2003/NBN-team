package com.nbn.adfeed.ui.feed;

import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.ImageView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nbn.adfeed.R;
import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionState;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;

import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class FeedAdapterMediaBindingTest {
    @Test
    public void bindingImageCardLoadsMediaDrawableIntoImageView() {
        FeedAdapter adapter = new FeedAdapter(noopListener());
        RecyclerView parent = recyclerView();
        RecyclerView.ViewHolder holder = adapter.onCreateViewHolder(parent, 1);
        AdItem ad = ad("ad_001", AdContentType.LARGE_IMAGE);

        ((FeedAdViewHolder) holder).bind(ad, noopListener(), Collections.emptySet(), InteractionStore.get());

        ImageView mediaImage = holder.itemView.findViewById(R.id.mediaImage);
        assertNotNull(mediaImage.getDrawable());
    }

    @Test
    public void bindingVideoCardLoadsCoverDrawableIntoImageView() {
        FeedAdapter adapter = new FeedAdapter(noopListener());
        RecyclerView parent = recyclerView();
        RecyclerView.ViewHolder holder = adapter.onCreateViewHolder(parent, 3);
        AdItem ad = ad("ad_003", AdContentType.VIDEO);

        ((FeedAdViewHolder) holder).bind(ad, noopListener(), Collections.emptySet(), InteractionStore.get());

        ImageView mediaImage = holder.itemView.findViewById(R.id.mediaImage);
        assertNotNull(mediaImage.getDrawable());
    }

    private static RecyclerView recyclerView() {
        RecyclerView recyclerView = new RecyclerView(new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(),
                androidx.appcompat.R.style.Theme_AppCompat
        ));
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        return recyclerView;
    }

    private static AdItem ad(String id, AdContentType contentType) {
        return new AdItem(
                id,
                "测试广告",
                "NBN",
                "精选",
                "测试摘要",
                contentType,
                Collections.singletonList("测试"),
                new InteractionState()
        );
    }

    private static FeedInteractionListener noopListener() {
        return new FeedInteractionListener() {
            @Override
            public void onCardClick(AdItem ad, int position) {
            }

            @Override
            public void onLikeClick(AdItem ad, int position) {
            }

            @Override
            public void onCollectClick(AdItem ad, int position) {
            }

            @Override
            public void onShareClick(AdItem ad, int position) {
            }

            @Override
            public void onTagClick(AdItem ad, String tag, int position) {
            }

            @Override
            public void onVideoPlayClick(AdItem ad, int position) {
            }

            @Override
            public void onVideoCardDetached(AdItem ad) {
            }
        };
    }
}
