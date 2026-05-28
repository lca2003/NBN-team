package com.nbn.adfeed.data.remote;

public final class RemoteConfig {
    public static final int DEFAULT_TIMEOUT_MILLIS = 3000;
    public static final int DEFAULT_MAX_RETRIES = 2;

    private final int timeoutMillis;
    private final int maxRetries;

    public RemoteConfig(int timeoutMillis, int maxRetries) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be greater than 0");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
        this.timeoutMillis = timeoutMillis;
        this.maxRetries = maxRetries;
    }

    public static RemoteConfig defaults() {
        return new RemoteConfig(DEFAULT_TIMEOUT_MILLIS, DEFAULT_MAX_RETRIES);
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    public int getMaxRetries() {
        return maxRetries;
    }
}
