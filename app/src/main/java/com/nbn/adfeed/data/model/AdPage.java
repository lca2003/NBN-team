package com.nbn.adfeed.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AdPage {
    private final List<AdItem> items;
    private final String nextCursor;
    private final boolean hasMore;

    public AdPage(List<AdItem> items, String nextCursor, boolean hasMore) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public List<AdItem> getItems() {
        return items;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public boolean hasMore() {
        return hasMore;
    }
}
