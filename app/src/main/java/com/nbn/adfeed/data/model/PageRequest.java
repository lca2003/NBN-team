package com.nbn.adfeed.data.model;

public final class PageRequest {
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final String FIRST_CURSOR = "page_1";

    private final String channel;
    private final String cursor;
    private final int pageSize;

    public PageRequest(String channel, String cursor, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than 0");
        }
        this.channel = channel;
        this.cursor = normalizeCursor(cursor);
        this.pageSize = pageSize;
    }

    public static PageRequest firstPage(String channel, int pageSize) {
        return new PageRequest(channel, FIRST_CURSOR, pageSize);
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
        if (cursor == null || cursor.trim().isEmpty()) {
            return FIRST_CURSOR;
        }
        return cursor.trim();
    }
}
