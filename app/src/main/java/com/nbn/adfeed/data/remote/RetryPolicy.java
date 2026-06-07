package com.nbn.adfeed.data.remote;

public final class RetryPolicy {
    private final RemoteConfig config;

    public RetryPolicy(RemoteConfig config) {
        this.config = config;
    }

    public boolean shouldRetry(RemoteAdException exception, int completedAttempts) {
        if (completedAttempts >= config.getMaxRetries()) {
            return false;
        }
        RemoteAdException.Reason reason = exception.getReason();
        return reason == RemoteAdException.Reason.TIMEOUT || reason == RemoteAdException.Reason.NETWORK;
    }
}
