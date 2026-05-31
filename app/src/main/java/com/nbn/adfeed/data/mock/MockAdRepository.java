package com.nbn.adfeed.data.mock;

import android.content.Context;

import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.AdStats;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.InteractionAction;
import com.nbn.adfeed.data.model.InteractionState;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.model.SearchRequest;
import com.nbn.adfeed.data.repository.AdRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MockAdRepository implements AdRepository {
    interface DefaultJsonLoader {
        String load() throws IOException;
    }

    interface FixtureFallback {
        List<AdItem> load();
    }

    public static final String SOURCE = "mock";
    private static final String ALL_CHANNEL = "全部";
    private static final String DEFAULT_ASSET_NAME = "ads_mock.json";
    private static final String MODULE_ASSET_PATH = "src/main/assets/" + DEFAULT_ASSET_NAME;
    private static final String DEFAULT_SOURCE_TREE_ASSET_PATH = "app/src/main/assets/" + DEFAULT_ASSET_NAME;

    private final Map<String, AdItem> adsById = new LinkedHashMap<>();

    public MockAdRepository() {
        this(loadDefaultAds());
    }

    public MockAdRepository(List<AdItem> ads) {
        replaceAds(ads);
    }

    public static MockAdRepository fromJson(String json) {
        return new MockAdRepository(MockAdJsonParser.parse(json));
    }

    public static MockAdRepository fromAsset(Context context) {
        return new MockAdRepository(loadDefaultAds(context));
    }

    @Override
    public synchronized DataResult<PageResult<AdItem>> loadAds(PageRequest request) {
        PageRequest safeRequest = request == null ? PageRequest.firstPage("", PageRequest.DEFAULT_PAGE_SIZE) : request;
        List<AdItem> filtered = filterByChannel(safeRequest.getChannel());
        PageResult<AdItem> page = page(filtered, safeRequest);
        if (page.isEmpty()) {
            return DataResult.empty(page, SOURCE, "No ads for requested page or channel");
        }
        return DataResult.success(page, SOURCE);
    }

    @Override
    public synchronized DataResult<AdItem> getAdById(String adId) {
        AdItem ad = adsById.get(normalizeId(adId));
        if (ad == null) {
            return DataResult.empty(null, SOURCE, "Ad not found: " + adId);
        }
        return DataResult.success(ad, SOURCE);
    }

    @Override
    public synchronized DataResult<PageResult<AdItem>> searchAds(SearchRequest request) {
        SearchRequest safeRequest = request == null ? SearchRequest.keyword("") : request;
        List<AdItem> matches = filterByChannel(safeRequest.getChannel());
        matches = filterByTag(matches, safeRequest.getTag());
        matches = filterByKeyword(matches, safeRequest.getQuery());
        PageResult<AdItem> page = page(matches, safeRequest.toPageRequest());
        if (page.isEmpty()) {
            return DataResult.empty(page, SOURCE, "No ads matched search");
        }
        return DataResult.success(page, SOURCE);
    }

    @Override
    public synchronized DataResult<AdItem> updateInteraction(String adId, InteractionAction action) {
        String normalizedId = normalizeId(adId);
        AdItem current = adsById.get(normalizedId);
        if (current == null) {
            return DataResult.empty(null, SOURCE, "Ad not found: " + adId);
        }
        AdItem updated = applyAction(current, action == null ? InteractionAction.CLICK : action);
        adsById.put(normalizedId, updated);
        return DataResult.success(updated, SOURCE);
    }

    public synchronized List<AdItem> snapshot() {
        return new ArrayList<>(adsById.values());
    }

    private void replaceAds(List<AdItem> ads) {
        adsById.clear();
        for (AdItem ad : ads == null ? Collections.<AdItem>emptyList() : ads) {
            adsById.put(normalizeId(ad.getId()), ad);
        }
    }

    private List<AdItem> filterByChannel(String channel) {
        String normalizedChannel = normalize(channel);
        List<AdItem> result = new ArrayList<>();
        for (AdItem ad : adsById.values()) {
            if (normalizedChannel.isEmpty()
                    || normalize(ALL_CHANNEL).equals(normalizedChannel)
                    || normalize(ad.getChannel()).equals(normalizedChannel)
                    || normalize(ad.getChannelId()).equals(normalizedChannel)) {
                result.add(ad);
            }
        }
        return result;
    }

    private static List<AdItem> filterByTag(List<AdItem> ads, String tag) {
        String normalizedTag = normalize(tag);
        if (normalizedTag.isEmpty()) {
            return ads;
        }
        List<AdItem> result = new ArrayList<>();
        for (AdItem ad : ads) {
            for (String adTag : ad.getTags()) {
                if (normalize(adTag).equals(normalizedTag) || normalize(adTag).contains(normalizedTag)) {
                    result.add(ad);
                    break;
                }
            }
        }
        return result;
    }

    private static List<AdItem> filterByKeyword(List<AdItem> ads, String keyword) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isEmpty()) {
            return ads;
        }
        List<AdItem> result = new ArrayList<>();
        for (AdItem ad : ads) {
            if (matches(ad, normalizedKeyword)) {
                result.add(ad);
            }
        }
        return result;
    }

    private static boolean matches(AdItem ad, String normalizedKeyword) {
        if (contains(normalizedKeyword, ad.getTitle())
                || contains(normalizedKeyword, ad.getBrand())
                || contains(normalizedKeyword, ad.getChannel())
                || contains(normalizedKeyword, ad.getDescription())
                || contains(normalizedKeyword, ad.getSummary())) {
            return true;
        }
        for (String tag : ad.getTags()) {
            if (contains(normalizedKeyword, tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String normalizedKeyword, String value) {
        String normalizedValue = normalize(value);
        return !normalizedValue.isEmpty()
                && (normalizedKeyword.contains(normalizedValue) || normalizedValue.contains(normalizedKeyword));
    }

    private static PageResult<AdItem> page(List<AdItem> filtered, PageRequest request) {
        int pageNumber = request.getPageNumber();
        int pageSize = request.getPageSize();
        int start = (pageNumber - 1) * pageSize;
        if (start >= filtered.size()) {
            return new PageResult<>(new ArrayList<>(), request.getCursor(), null, false,
                    pageNumber, pageSize, filtered.size(), SOURCE);
        }

        int end = Math.min(start + pageSize, filtered.size());
        boolean hasMore = end < filtered.size();
        String nextCursor = hasMore ? "page_" + (pageNumber + 1) : null;
        return new PageResult<>(filtered.subList(start, end), request.getCursor(), nextCursor, hasMore,
                pageNumber, pageSize, filtered.size(), SOURCE);
    }

    private static AdItem applyAction(AdItem current, InteractionAction action) {
        InteractionState state = current.getInteractionState();
        AdStats stats = current.getStats();
        switch (action) {
            case LIKE:
                return current.withInteractionAndStats(state.withLiked(true),
                        state.isLiked() ? stats : stats.increaseLike());
            case UNLIKE:
                return current.withInteractionAndStats(state.withLiked(false),
                        state.isLiked() ? stats.decreaseLike() : stats);
            case TOGGLE_LIKE:
                return state.isLiked()
                        ? applyAction(current, InteractionAction.UNLIKE)
                        : applyAction(current, InteractionAction.LIKE);
            case COLLECT:
                return current.withInteractionAndStats(state.withCollected(true),
                        state.isCollected() ? stats : stats.increaseCollect());
            case UNCOLLECT:
                return current.withInteractionAndStats(state.withCollected(false),
                        state.isCollected() ? stats.decreaseCollect() : stats);
            case TOGGLE_COLLECT:
                return state.isCollected()
                        ? applyAction(current, InteractionAction.UNCOLLECT)
                        : applyAction(current, InteractionAction.COLLECT);
            case EXPOSE:
                return current.withStats(stats.increaseExposure());
            case SHARE:
                return current.withStats(stats.increaseShare());
            case CLICK:
            default:
                return current.withStats(stats.increaseClick());
        }
    }

    private static String readAsset(Context context, String fileName) throws IOException {
        try (InputStream inputStream = context.getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }

    private static List<AdItem> loadDefaultAds() {
        return loadDefaultAds(
                () -> readDefaultJson(null),
                MockAdFixtures::createAds
        );
    }

    private static List<AdItem> loadDefaultAds(Context context) {
        return loadDefaultAds(
                () -> readDefaultJson(context),
                MockAdFixtures::createAds
        );
    }

    static List<AdItem> loadDefaultAds(DefaultJsonLoader jsonLoader, FixtureFallback fixtureFallback) {
        try {
            String json = jsonLoader == null ? null : jsonLoader.load();
            if (json != null && !json.trim().isEmpty()) {
                return MockAdJsonParser.parse(json);
            }
        } catch (RuntimeException | IOException ignored) {
            // Fall through to fixtures when the asset bundle cannot be resolved in the current runtime.
        }
        return fixtureFallback == null ? Collections.<AdItem>emptyList() : fixtureFallback.load();
    }

    private static String readDefaultJson(Context context) throws IOException {
        if (context != null) {
            return readAsset(context, DEFAULT_ASSET_NAME);
        }
        String classpathJson = readClasspathAsset("assets/" + DEFAULT_ASSET_NAME);
        if (classpathJson != null) {
            return classpathJson;
        }
        classpathJson = readClasspathAsset(DEFAULT_ASSET_NAME);
        if (classpathJson != null) {
            return classpathJson;
        }
        String sourceTreeJson = readSourceTreeAsset(MODULE_ASSET_PATH);
        if (sourceTreeJson != null) {
            return sourceTreeJson;
        }
        sourceTreeJson = readSourceTreeAsset(DEFAULT_SOURCE_TREE_ASSET_PATH);
        if (sourceTreeJson != null) {
            return sourceTreeJson;
        }
        return null;
    }

    private static String readSourceTreeAsset(String path) throws IOException {
        Path sourceTreeAsset = Paths.get(path);
        if (!Files.isRegularFile(sourceTreeAsset)) {
            return null;
        }
        return new String(Files.readAllBytes(sourceTreeAsset), StandardCharsets.UTF_8);
    }

    private static String readClasspathAsset(String resourcePath) throws IOException {
        ClassLoader classLoader = MockAdRepository.class.getClassLoader();
        if (classLoader == null) {
            return null;
        }
        try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                return builder.toString();
            }
        }
    }

    private static String normalizeId(String value) {
        return normalize(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
