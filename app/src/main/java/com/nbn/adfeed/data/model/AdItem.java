package com.nbn.adfeed.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AdItem {
    private final String id;
    private final String title;
    private final String brand;
    private final String channel;
    private final String summary;
    private final AdContentType contentType;
    private final List<String> tags;
    private final InteractionState interactionState;

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
        this.id = id;
        this.title = title;
        this.brand = brand;
        this.channel = channel;
        this.summary = summary;
        this.contentType = contentType;
        this.tags = Collections.unmodifiableList(new ArrayList<>(tags));
        this.interactionState = interactionState;
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

    public String getSummary() {
        return summary;
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
}
