package com.nbn.adfeed.ai;

public final class AiResponse<T> {
    private final T value;
    private final AiOutputSource source;
    private final boolean cached;
    private final String message;
    private final Throwable error;

    private AiResponse(T value, AiOutputSource source, boolean cached, String message, Throwable error) {
        this.value = value;
        this.source = source;
        this.cached = cached;
        this.message = message == null ? "" : message;
        this.error = error;
    }

    public static <T> AiResponse<T> success(T value, AiOutputSource source, boolean cached) {
        return new AiResponse<>(value, source, cached, "", null);
    }

    public static <T> AiResponse<T> failure(T fallbackValue, AiOutputSource source, String message, Throwable error) {
        return new AiResponse<>(fallbackValue, source, false, message, error);
    }

    public T getValue() {
        return value;
    }

    public AiOutputSource getSource() {
        return source;
    }

    public boolean isCached() {
        return cached;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getError() {
        return error;
    }

    public boolean isSuccess() {
        return value != null;
    }
}
