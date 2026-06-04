package com.nbn.adfeed.data.model;

public final class SearchRequest {
    private final String query;
    private final String channel;
    private final String tag;
    private final String cursor;
    private final int pageSize;
    private final String sourcePage;

    public SearchRequest(String query, String channel, String tag, String cursor, int pageSize, String sourcePage) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than 0");
        }
        this.query = normalize(query);
        this.channel = normalize(channel);
        this.tag = normalize(tag);
        this.cursor = normalize(cursor).isEmpty() ? PageRequest.FIRST_CURSOR : normalize(cursor);
        this.pageSize = pageSize;
        this.sourcePage = normalize(sourcePage);
    }

    public static SearchRequest keyword(String query) {
        return new SearchRequest(query, "", "", PageRequest.FIRST_CURSOR, PageRequest.DEFAULT_PAGE_SIZE, "search");
    }

    public static SearchRequest tag(String tag) {
        return new SearchRequest("", "", tag, PageRequest.FIRST_CURSOR, PageRequest.DEFAULT_PAGE_SIZE, "tag_filter");
    }

    public String getQuery() {
        return query;
    }

    public String getChannel() {
        return channel;
    }

    public String getTag() {
        return tag;
    }

    public String getCursor() {
        return cursor;
    }

    public int getPageSize() {
        return pageSize;
    }

    public String getSourcePage() {
        return sourcePage;
    }

    public PageRequest toPageRequest() {
        return new PageRequest(channel, cursor, pageSize, PageRequest.FIRST_CURSOR.equals(cursor), sourcePage);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
