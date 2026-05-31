package com.nbn.adfeed.data.model;

public final class PageRequest {
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final String FIRST_CURSOR = "page_1";

    private final String channel;
    private final String cursor;
    private final int pageSize;
    private final boolean refresh;
    private final String sourcePage;

    public PageRequest(String channel, String cursor, int pageSize) {
        this(channel, cursor, pageSize, false, "");
    }

    public PageRequest(String channel, String cursor, int pageSize, boolean refresh, String sourcePage) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than 0");
        }
        this.channel = normalize(channel);
        this.cursor = normalizeCursor(cursor);
        this.pageSize = pageSize;
        this.refresh = refresh;
        this.sourcePage = normalize(sourcePage);
    }

    public static PageRequest firstPage(String channel, int pageSize) {
        return new PageRequest(channel, FIRST_CURSOR, pageSize, true, "feed");
    }

    public static PageRequest nextPage(String channel, String cursor, int pageSize) {
        return new PageRequest(channel, cursor, pageSize, false, "feed");
    }

    public String getChannel() {
        return channel;
    }

    public String getCursor() {
        return cursor;
    }

    public int getPageSize() {
        return pageSize;
    }

    public boolean isRefresh() {
        return refresh || FIRST_CURSOR.equals(cursor);
    }

    public String getSourcePage() {
        return sourcePage;
    }

    public int getPageNumber() {
        if (!cursor.startsWith("page_")) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(cursor.substring("page_".length())));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static String normalizeCursor(String cursor) {
        String normalized = normalize(cursor);
        return normalized.isEmpty() ? FIRST_CURSOR : normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
