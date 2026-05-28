package com.nbn.adfeed.data.remote;

public final class RemoteAdException extends Exception {
    public enum Reason {
        TIMEOUT,
        NETWORK,
        INVALID_RESPONSE,
        UNKNOWN
    }

    private final Reason reason;

    public RemoteAdException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
