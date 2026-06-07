package com.nbn.adfeed.data.remote;

import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.DataResult;
import com.nbn.adfeed.data.model.InteractionAction;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.model.PageResult;
import com.nbn.adfeed.data.model.SearchRequest;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class BackendRemoteAdDataSource implements RemoteAdDataSource {
    public interface Transport {
        String get(String path) throws RemoteAdException;

        String post(String path) throws RemoteAdException;

        String delete(String path) throws RemoteAdException;
    }

    public static final String SOURCE = "backend";

    private final Transport transport;

    public BackendRemoteAdDataSource(Transport transport) {
        this.transport = transport == null ? defaultTransport() : transport;
    }

    public static BackendRemoteAdDataSource defaultDataSource() {
        return new BackendRemoteAdDataSource(defaultTransport());
    }

    @Override
    public DataResult<PageResult<AdItem>> loadAds(PageRequest request) throws RemoteAdException {
        PageRequest safeRequest = request == null
                ? PageRequest.firstPage("featured", PageRequest.DEFAULT_PAGE_SIZE)
                : request;
        String path = "/v1/feed?channel=" + encode(safeRequest.getChannel())
                + "&cursor=" + encode(safeRequest.getCursor())
                + "&limit=" + safeRequest.getPageSize();
        JSONObject data = BackendAdJsonParser.dataFromEnvelope(transport.get(path));
        PageResult<AdItem> page = BackendAdJsonParser.parseFeed(data, safeRequest, SOURCE);
        return page.isEmpty()
                ? DataResult.empty(page, SOURCE, "Backend returned empty feed")
                : DataResult.success(page, SOURCE);
    }

    @Override
    public DataResult<AdItem> getAdById(String adId) throws RemoteAdException {
        JSONObject data = BackendAdJsonParser.dataFromEnvelope(transport.get("/v1/ads/" + encode(adId)));
        JSONObject adJson = data.optJSONObject("ad");
        if (adJson == null) {
            return DataResult.empty(null, SOURCE, "Backend response missing ad");
        }
        return DataResult.success(BackendAdJsonParser.parseAd(adJson, ""), SOURCE);
    }

    @Override
    public DataResult<PageResult<AdItem>> searchAds(SearchRequest request) throws RemoteAdException {
        SearchRequest safeRequest = request == null ? SearchRequest.keyword("") : request;
        DataResult<PageResult<AdItem>> feed = loadAds(safeRequest.toPageRequest());
        PageResult<AdItem> page = feed.getData();
        if (page == null) {
            return DataResult.empty(null, SOURCE, "Backend search has no feed data");
        }
        List<AdItem> filtered = filter(page.getItems(), safeRequest);
        PageResult<AdItem> filteredPage = new PageResult<>(
                filtered,
                page.getCurrentCursor(),
                page.getNextCursor(),
                page.hasMore(),
                page.getPageNumber(),
                page.getPageSize(),
                filtered.size(),
                SOURCE
        );
        return filteredPage.isEmpty()
                ? DataResult.empty(filteredPage, SOURCE, "No backend search results")
                : DataResult.success(filteredPage, SOURCE);
    }

    @Override
    public DataResult<AdItem> updateInteraction(String adId, InteractionAction action) throws RemoteAdException {
        InteractionAction resolvedAction = resolveToggle(adId, action);
        String path = interactionPath(adId, resolvedAction);
        if (resolvedAction == InteractionAction.UNLIKE || resolvedAction == InteractionAction.UNCOLLECT) {
            transport.delete(path);
        } else {
            transport.post(path);
        }
        return getAdById(adId);
    }

    private InteractionAction resolveToggle(String adId, InteractionAction action) throws RemoteAdException {
        if (action == InteractionAction.TOGGLE_LIKE) {
            AdItem current = getAdById(adId).getData();
            return current != null && current.getInteractionState().isLiked()
                    ? InteractionAction.UNLIKE
                    : InteractionAction.LIKE;
        }
        if (action == InteractionAction.TOGGLE_COLLECT) {
            AdItem current = getAdById(adId).getData();
            return current != null && current.getInteractionState().isCollected()
                    ? InteractionAction.UNCOLLECT
                    : InteractionAction.COLLECT;
        }
        return action == null ? InteractionAction.CLICK : action;
    }

    private static Transport defaultTransport() {
        return new Transport() {
            @Override
            public String get(String path) throws RemoteAdException {
                return request(path, "GET");
            }

            @Override
            public String post(String path) throws RemoteAdException {
                return request(path, "POST");
            }

            @Override
            public String delete(String path) throws RemoteAdException {
                return request(path, "DELETE");
            }

            private String request(String path, String method) throws RemoteAdException {
                RemoteAdException lastException = null;
                for (BackendConfig candidate : BackendConfig.defaultCandidates()) {
                    try {
                        HttpApiClient client = new HttpApiClient(candidate);
                        if ("POST".equals(method)) {
                            return client.post(path);
                        }
                        if ("DELETE".equals(method)) {
                            return client.delete(path);
                        }
                        return client.get(path);
                    } catch (RemoteAdException exception) {
                        lastException = exception;
                    }
                }
                throw lastException == null
                        ? new RemoteAdException(RemoteAdException.Reason.NETWORK, "Backend unavailable")
                        : lastException;
            }
        };
    }

    private static String interactionPath(String adId, InteractionAction action) {
        String encodedAdId = encode(adId);
        return switch (action) {
            case LIKE -> "/v1/ads/" + encodedAdId + "/like";
            case UNLIKE -> "/v1/ads/" + encodedAdId + "/like";
            case COLLECT -> "/v1/ads/" + encodedAdId + "/collect";
            case UNCOLLECT -> "/v1/ads/" + encodedAdId + "/collect";
            case CLICK -> "/v1/ads/" + encodedAdId + "/click";
            case EXPOSE -> "/v1/ads/" + encodedAdId + "/exposure";
            case SHARE -> "/v1/ads/" + encodedAdId + "/share";
            case TOGGLE_LIKE, TOGGLE_COLLECT -> throw new IllegalArgumentException("Toggle action must be resolved first");
        };
    }

    private static List<AdItem> filter(List<AdItem> items, SearchRequest request) {
        String query = normalize(request.getQuery());
        String tag = normalize(request.getTag());
        if (query.isEmpty() && tag.isEmpty()) {
            return items;
        }
        List<AdItem> filtered = new ArrayList<>();
        for (AdItem item : items) {
            if (matches(item, query, tag)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private static boolean matches(AdItem item, String query, String tag) {
        String haystack = normalize(item.getTitle() + " " + item.getBrand() + " "
                + item.getDescription() + " " + item.getSummary() + " " + item.getTags());
        return (query.isEmpty() || haystack.contains(query)) && (tag.isEmpty() || haystack.contains(tag));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException exception) {
            return "";
        }
    }
}
