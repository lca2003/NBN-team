package com.nbn.adfeed.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AdItem {
    private final String id;
    private final String title;
    private final String brand;
    private final String channel;
    private final String channelId;
    private final String description;
    private final String summary;
    private final String imageUrl;
    private final String thumbnailUrl;
    private final String videoUrl;
    private final String offerText;
    private final String ctaText;
    private final String skuText;
    private final String ratingText;
    private final String deliveryText;
    private final String stockText;
    private final List<String> similarItems;
    private final String distanceText;
    private final String districtText;
    private final String addressText;
    private final String businessHoursText;
    private final String navigationText;
    private final String assetTheme;
    private final String visualLabel;
    private final String ctaIntent;
    private final AdContentType contentType;
    private final List<String> tags;
    private final InteractionState interactionState;
    private final AdStats stats;
    private final String contentHash;

    public AdItem(
            String id,
            String title,
            String brand,
            String channel,
            String summary,
            AdContentType contentType,
            List<String> tags,
            InteractionState interactionState
    ) {
        this(id, title, brand, channel, channel, summary, summary, null, null, null,
                contentType, tags, interactionState, AdStats.empty(), null);
    }

    public AdItem(
            String id,
            String title,
            String brand,
            String channel,
            String description,
            String summary,
            String imageUrl,
            String videoUrl,
            AdContentType contentType,
            List<String> tags,
            InteractionState interactionState
    ) {
        this(id, title, brand, channel, channel, description, summary, imageUrl, imageUrl, videoUrl,
                contentType, tags, interactionState, AdStats.empty(), null);
    }

    public AdItem(
            String id,
            String title,
            String brand,
            String channel,
            String channelId,
            String description,
            String summary,
            String imageUrl,
            String thumbnailUrl,
            String videoUrl,
            AdContentType contentType,
            List<String> tags,
            InteractionState interactionState,
            AdStats stats,
            String contentHash
    ) {
        this(id, title, brand, channel, channelId, description, summary, imageUrl, thumbnailUrl, videoUrl,
                "", "", contentType, tags, interactionState, stats, contentHash);
    }

    public AdItem(
            String id,
            String title,
            String brand,
            String channel,
            String channelId,
            String description,
            String summary,
            String imageUrl,
            String thumbnailUrl,
            String videoUrl,
            String offerText,
            String ctaText,
            AdContentType contentType,
            List<String> tags,
            InteractionState interactionState,
            AdStats stats,
            String contentHash
    ) {
        this(id, title, brand, channel, channelId, description, summary, imageUrl, thumbnailUrl, videoUrl,
                offerText, ctaText, "", "", "", "", Collections.emptyList(),
                "", "", "", "", "",
                "", "", "",
                contentType, tags, interactionState, stats, contentHash);
    }

    public AdItem(
            String id,
            String title,
            String brand,
            String channel,
            String channelId,
            String description,
            String summary,
            String imageUrl,
            String thumbnailUrl,
            String videoUrl,
            String offerText,
            String ctaText,
            String skuText,
            String ratingText,
            String deliveryText,
            String stockText,
            List<String> similarItems,
            String distanceText,
            String districtText,
            String addressText,
            String businessHoursText,
            String navigationText,
            AdContentType contentType,
            List<String> tags,
            InteractionState interactionState,
            AdStats stats,
            String contentHash
    ) {
        this(id, title, brand, channel, channelId, description, summary, imageUrl, thumbnailUrl, videoUrl,
                offerText, ctaText, skuText, ratingText, deliveryText, stockText, similarItems,
                distanceText, districtText, addressText, businessHoursText, navigationText,
                "", "", "",
                contentType, tags, interactionState, stats, contentHash);
    }

    public AdItem(
            String id,
            String title,
            String brand,
            String channel,
            String channelId,
            String description,
            String summary,
            String imageUrl,
            String thumbnailUrl,
            String videoUrl,
            String offerText,
            String ctaText,
            String skuText,
            String ratingText,
            String deliveryText,
            String stockText,
            List<String> similarItems,
            String distanceText,
            String districtText,
            String addressText,
            String businessHoursText,
            String navigationText,
            String assetTheme,
            String visualLabel,
            String ctaIntent,
            AdContentType contentType,
            List<String> tags,
            InteractionState interactionState,
            AdStats stats,
            String contentHash
    ) {
        this.id = requireText(id, "id");
        this.title = safe(title);
        this.brand = safe(brand);
        this.channel = safe(channel);
        this.channelId = safe(channelId).isEmpty() ? this.channel : safe(channelId);
        this.description = safe(description);
        this.summary = safe(summary);
        this.imageUrl = emptyToNull(imageUrl);
        this.thumbnailUrl = emptyToNull(thumbnailUrl);
        this.videoUrl = emptyToNull(videoUrl);
        this.offerText = safe(offerText);
        this.ctaText = safe(ctaText);
        this.skuText = safe(skuText);
        this.ratingText = safe(ratingText);
        this.deliveryText = safe(deliveryText);
        this.stockText = safe(stockText);
        this.similarItems = Collections.unmodifiableList(new ArrayList<>(
                similarItems == null ? Collections.emptyList() : similarItems
        ));
        this.distanceText = safe(distanceText);
        this.districtText = safe(districtText);
        this.addressText = safe(addressText);
        this.businessHoursText = safe(businessHoursText);
        this.navigationText = safe(navigationText);
        this.assetTheme = safe(assetTheme);
        this.visualLabel = safe(visualLabel);
        this.ctaIntent = safe(ctaIntent);
        this.contentType = contentType == null ? AdContentType.LARGE_IMAGE : contentType;
        this.tags = Collections.unmodifiableList(new ArrayList<>(tags == null ? Collections.emptyList() : tags));
        this.interactionState = interactionState == null ? InteractionState.empty() : interactionState;
        this.stats = stats == null ? AdStats.empty() : stats;
        this.contentHash = safe(contentHash).isEmpty() ? buildContentHash() : contentHash;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBrand() {
        return brand;
    }

    public String getChannel() {
        return channel;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getDescription() {
        return description;
    }

    public String getSummary() {
        return summary;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getOfferText() {
        return offerText;
    }

    public String getCtaText() {
        return ctaText;
    }

    public String getSkuText() {
        return skuText;
    }

    public String getRatingText() {
        return ratingText;
    }

    public String getDeliveryText() {
        return deliveryText;
    }

    public String getStockText() {
        return stockText;
    }

    public List<String> getSimilarItems() {
        return similarItems;
    }

    public String getDistanceText() {
        return distanceText;
    }

    public String getDistrictText() {
        return districtText;
    }

    public String getAddressText() {
        return addressText;
    }

    public String getBusinessHoursText() {
        return businessHoursText;
    }

    public String getNavigationText() {
        return navigationText;
    }

    public String getAssetTheme() {
        return assetTheme;
    }

    public String getVisualLabel() {
        return visualLabel;
    }

    public String getCtaIntent() {
        return ctaIntent;
    }

    public AdContentType getContentType() {
        return contentType;
    }

    public List<String> getTags() {
        return tags;
    }

    public InteractionState getInteractionState() {
        return interactionState;
    }

    public AdStats getStats() {
        return stats;
    }

    public String getContentHash() {
        return contentHash;
    }

    public AdItem withInteractionState(InteractionState nextState) {
        return copy(nextState, stats, summary, tags);
    }

    public AdItem withStats(AdStats nextStats) {
        return copy(interactionState, nextStats, summary, tags);
    }

    public AdItem withSummary(String nextSummary) {
        return copy(interactionState, stats, nextSummary, tags);
    }

    public AdItem withTags(List<String> nextTags) {
        return copy(interactionState, stats, summary, nextTags);
    }

    public AdItem withInteractionAndStats(InteractionState nextState, AdStats nextStats) {
        return copy(nextState, nextStats, summary, tags);
    }

    private AdItem copy(InteractionState nextState, AdStats nextStats, String nextSummary, List<String> nextTags) {
        return new AdItem(id, title, brand, channel, channelId, description, nextSummary, imageUrl,
                thumbnailUrl, videoUrl, offerText, ctaText, skuText, ratingText, deliveryText, stockText,
                similarItems, distanceText, districtText, addressText, businessHoursText, navigationText,
                assetTheme, visualLabel, ctaIntent,
                contentType, nextTags, nextState, nextStats, null);
    }

    private String buildContentHash() {
        return Integer.toHexString(Objects.hash(title, brand, channel, description, summary,
                offerText, ctaText, skuText, ratingText, deliveryText, stockText, similarItems,
                distanceText, districtText, addressText, businessHoursText, navigationText,
                assetTheme, visualLabel, ctaIntent,
                tags, contentType));
    }

    private static String requireText(String value, String name) {
        String normalized = safe(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String emptyToNull(String value) {
        String normalized = safe(value);
        return normalized.isEmpty() ? null : normalized;
    }
}
