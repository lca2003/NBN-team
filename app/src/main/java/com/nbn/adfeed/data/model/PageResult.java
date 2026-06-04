package com.nbn.adfeed.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PageResult<T> {
    private final List<T> items;
    private final String currentCursor;
    private final String nextCursor;
    private final boolean hasMore;
    private final int pageNumber;
    private final int pageSize;
    private final int totalCount;
    private final String dataSource;

    public PageResult(
            List<T> items,
            String currentCursor,
            String nextCursor,
            boolean hasMore,
            int pageNumber,
            int pageSize,
            int totalCount,
            String dataSource
    ) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items == null ? Collections.emptyList() : items));
        this.currentCursor = currentCursor;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
        this.pageNumber = Math.max(1, pageNumber);
        this.pageSize = Math.max(1, pageSize);
        this.totalCount = Math.max(0, totalCount);
        this.dataSource = dataSource == null ? "" : dataSource;
    }

    public static <T> PageResult<T> empty(PageRequest request, String dataSource) {
        return new PageResult<>(Collections.emptyList(), request.getCursor(), null, false,
                request.getPageNumber(), request.getPageSize(), 0, dataSource);
    }

    public List<T> getItems() {
        return items;
    }

    public String getCurrentCursor() {
        return currentCursor;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public boolean hasMore() {
        return hasMore;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public String getDataSource() {
        return dataSource;
    }
}
