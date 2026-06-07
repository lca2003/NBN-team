package com.nbn.adfeed.data.model;

public final class DataResult<T> {
    public enum Status {
        SUCCESS,
        EMPTY,
        TIMEOUT,
        PARSE_ERROR,
        REMOTE_ERROR,
        FALLBACK
    }

    private final Status status;
    private final T data;
    private final String source;
    private final String message;
    private final Throwable error;

    private DataResult(Status status, T data, String source, String message, Throwable error) {
        this.status = status;
        this.data = data;
        this.source = source == null ? "" : source;
        this.message = message == null ? "" : message;
        this.error = error;
    }

    public static <T> DataResult<T> success(T data, String source) {
        return new DataResult<>(Status.SUCCESS, data, source, "", null);
    }

    public static <T> DataResult<T> empty(T data, String source, String message) {
        return new DataResult<>(Status.EMPTY, data, source, message, null);
    }

    public static <T> DataResult<T> timeout(String source, String message, Throwable error) {
        return new DataResult<>(Status.TIMEOUT, null, source, message, error);
    }

    public static <T> DataResult<T> parseError(String source, String message, Throwable error) {
        return new DataResult<>(Status.PARSE_ERROR, null, source, message, error);
    }

    public static <T> DataResult<T> remoteError(String source, String message, Throwable error) {
        return new DataResult<>(Status.REMOTE_ERROR, null, source, message, error);
    }

    public static <T> DataResult<T> fallback(T data, String source, String message, Throwable error) {
        return new DataResult<>(Status.FALLBACK, data, source, message, error);
    }

    public Status getStatus() {
        return status;
    }

    public T getData() {
        return data;
    }

    public String getSource() {
        return source;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getError() {
        return error;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS || status == Status.FALLBACK;
    }

    public boolean isEmpty() {
        return status == Status.EMPTY;
    }

    public boolean isFallback() {
        return status == Status.FALLBACK;
    }

    public boolean hasData() {
        return data != null;
    }
}
