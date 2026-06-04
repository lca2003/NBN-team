package com.nbn.adfeed.data.remote;

import java.util.ArrayList;
import java.util.List;

public final class BackendConfig {
    public static final String DEFAULT_BASE_URL = "http://10.0.2.2:8080";
    public static final String ADB_REVERSE_BASE_URL = "http://127.0.0.1:8080";

    private final String apiBaseUrl;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int retryCount;
    private final boolean useMockFallback;

    public BackendConfig(
            String apiBaseUrl,
            int connectTimeoutMs,
            int readTimeoutMs,
            int retryCount,
            boolean useMockFallback
    ) {
        this.apiBaseUrl = normalizeBaseUrl(apiBaseUrl);
        this.connectTimeoutMs = Math.max(250, connectTimeoutMs);
        this.readTimeoutMs = Math.max(250, readTimeoutMs);
        this.retryCount = Math.max(0, retryCount);
        this.useMockFallback = useMockFallback;
    }

    public static BackendConfig defaultConfig() {
        return new BackendConfig(
                System.getProperty("nbn.api.baseUrl", DEFAULT_BASE_URL),
                integerProperty("nbn.api.connectTimeoutMs", 1_500),
                integerProperty("nbn.api.readTimeoutMs", 2_500),
                integerProperty("nbn.api.retryCount", 1),
                booleanProperty("nbn.api.useMockFallback", true)
        );
    }

    public static List<BackendConfig> defaultCandidates() {
        List<BackendConfig> candidates = new ArrayList<>();
        String configuredBaseUrl = System.getProperty("nbn.api.baseUrl", "").trim();
        if (!configuredBaseUrl.isEmpty()) {
            candidates.add(new BackendConfig(
                    configuredBaseUrl,
                    integerProperty("nbn.api.connectTimeoutMs", 1_500),
                    integerProperty("nbn.api.readTimeoutMs", 2_500),
                    integerProperty("nbn.api.retryCount", 1),
                    booleanProperty("nbn.api.useMockFallback", true)
            ));
            return candidates;
        }
        candidates.add(new BackendConfig(DEFAULT_BASE_URL, 900, 1_500, 0, true));
        candidates.add(new BackendConfig(ADB_REVERSE_BASE_URL, 900, 1_500, 0, true));
        return candidates;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public boolean isUseMockFallback() {
        return useMockFallback;
    }

    private static String normalizeBaseUrl(String apiBaseUrl) {
        String normalized = apiBaseUrl == null || apiBaseUrl.trim().isEmpty()
                ? DEFAULT_BASE_URL
                : apiBaseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static int integerProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(System.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static boolean booleanProperty(String key, boolean defaultValue) {
        return Boolean.parseBoolean(System.getProperty(key, String.valueOf(defaultValue)));
    }
}
