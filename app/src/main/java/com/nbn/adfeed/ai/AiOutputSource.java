package com.nbn.adfeed.ai;

public enum AiOutputSource {
    REMOTE_AI("remote_ai"),
    CACHE("cache"),
    RULE_FALLBACK("rule_fallback"),
    MOCK_FALLBACK("mock_fallback");

    private final String wireName;

    AiOutputSource(String wireName) {
        this.wireName = wireName;
    }

    public String getWireName() {
        return wireName;
    }
}
