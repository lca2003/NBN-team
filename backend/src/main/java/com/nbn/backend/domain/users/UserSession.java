package com.nbn.backend.domain.users;

public final class UserSession {
    private final ThreadLocal<String> requestUserId = new ThreadLocal<>();
    private String currentUserId;

    public UserSession(String currentUserId) {
        this.currentUserId = safe(currentUserId);
    }

    public synchronized String currentUserId() {
        String overrideUserId = requestUserId.get();
        return overrideUserId == null ? currentUserId : overrideUserId;
    }

    public synchronized String requireCurrentUserId() {
        String userId = currentUserId();
        if (userId.isBlank()) {
            throw new IllegalArgumentException("user not logged in");
        }
        return userId;
    }

    public synchronized boolean authenticated() {
        return !currentUserId().isBlank();
    }

    public synchronized void login(String userId) {
        String normalized = safe(userId);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("user not found");
        }
        currentUserId = normalized;
    }

    public synchronized void logout() {
        currentUserId = "";
    }

    public synchronized String persistentCurrentUserId() {
        return currentUserId;
    }

    public void beginRequest(String userId) {
        String normalized = safe(userId);
        if (normalized.isBlank()) {
            requestUserId.remove();
            return;
        }
        requestUserId.set(normalized);
    }

    public void clearRequest() {
        requestUserId.remove();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
