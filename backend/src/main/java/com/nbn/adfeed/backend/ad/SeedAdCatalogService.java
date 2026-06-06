package com.nbn.adfeed.backend.ad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public final class SeedAdCatalogService implements AdCatalogService {
    private static final String DEFAULT_RESOURCE_PATH = "seed/home_feed.json";

    private final List<AdItem> ads;

    @Autowired
    public SeedAdCatalogService(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_RESOURCE_PATH);
    }

    public SeedAdCatalogService(ObjectMapper objectMapper, String resourcePath) {
        this.ads = List.copyOf(loadAds(objectMapper, resourcePath));
    }

    @Override
    public List<AdItem> findAll() {
        return ads;
    }

    private static List<AdItem> loadAds(ObjectMapper objectMapper, String resourcePath) {
        try (InputStream inputStream = openResource(resourcePath)) {
            JsonNode root = objectMapper.readTree(inputStream);
            JsonNode items = root.path("page").path("items");
            if (!items.isArray()) {
                return List.of();
            }
            List<AdItem> result = new ArrayList<>();
            for (JsonNode item : items) {
                result.add(toAdItem(item));
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load ad seed resource: " + resourcePath, exception);
        }
    }

    private static InputStream openResource(String resourcePath) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = contextClassLoader == null ? null : contextClassLoader.getResourceAsStream(resourcePath);
        if (inputStream == null) {
            inputStream = SeedAdCatalogService.class.getClassLoader().getResourceAsStream(resourcePath);
        }
        if (inputStream == null) {
            throw new IllegalStateException("Missing ad seed resource: " + resourcePath);
        }
        return inputStream;
    }

    private static AdItem toAdItem(JsonNode item) {
        return new AdItem(
                item.path("adId").asText(),
                item.path("title").asText(),
                item.path("brand").asText(),
                channel(item),
                item.path("description").asText(),
                contentType(item.path("adType").asText()),
                tagNames(item.path("tags")),
                new InteractionState()
        );
    }

    private static String channel(JsonNode item) {
        String channel = item.path("channel").asText("").trim();
        if (!channel.isBlank()) {
            return channel;
        }
        channel = item.path("category").asText("").trim();
        if (!channel.isBlank()) {
            return channel;
        }
        JsonNode channelIds = item.path("channelIds");
        if (channelIds.isArray() && !channelIds.isEmpty()) {
            return channelIds.get(0).asText("");
        }
        return "";
    }

    private static AdContentType contentType(String rawType) {
        try {
            return AdContentType.valueOf(rawType);
        } catch (RuntimeException exception) {
            return AdContentType.SMALL_IMAGE;
        }
    }

    private static List<String> tagNames(JsonNode tags) {
        if (!tags.isArray()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (JsonNode tag : tags) {
            String name = tag.path("name").asText("").trim();
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }
}
