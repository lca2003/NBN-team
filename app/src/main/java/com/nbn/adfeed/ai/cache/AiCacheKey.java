package com.nbn.adfeed.ai.cache;

import com.nbn.adfeed.data.model.AdItem;

import java.util.Objects;

public final class AiCacheKey {
    private final String adId;
    private final String contentHash;
    private final String promptVersion;

    public AiCacheKey(String adId, String contentHash, String promptVersion) {
        this.adId = adId;
        this.contentHash = contentHash;
        this.promptVersion = promptVersion;
    }

    public static AiCacheKey forAd(AdItem item, String promptVersion) {
        String contentHash = item.getContentHash();
        if (contentHash == null || contentHash.trim().isEmpty()) {
            contentHash = Integer.toHexString(Objects.hash(
                    item.getTitle(),
                    item.getBrand(),
                    item.getDescription(),
                    item.getSummary(),
                    item.getTags()
            ));
        }
        return new AiCacheKey(item.getId(), contentHash, promptVersion);
    }

    public String getAdId() {
        return adId;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AiCacheKey)) {
            return false;
        }
        AiCacheKey that = (AiCacheKey) other;
        return Objects.equals(adId, that.adId)
                && Objects.equals(contentHash, that.contentHash)
                && Objects.equals(promptVersion, that.promptVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(adId, contentHash, promptVersion);
    }
}
