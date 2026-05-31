package com.nbn.adfeed.data.remote;

import org.json.JSONException;
import org.json.JSONObject;

public final class BackendStitchDataSource {
    public interface Transport {
        String get(String path) throws RemoteAdException;
    }

    private final Transport transport;

    public BackendStitchDataSource(BackendConfig config) {
        this(new HttpApiClient(config));
    }

    public static BackendStitchDataSource defaultDataSource() {
        return new BackendStitchDataSource(path -> {
            RemoteAdException lastException = null;
            for (BackendConfig candidate : BackendConfig.defaultCandidates()) {
                try {
                    return new HttpApiClient(candidate).get(path);
                } catch (RemoteAdException exception) {
                    lastException = exception;
                }
            }
            throw lastException == null
                    ? new RemoteAdException(RemoteAdException.Reason.NETWORK, "Backend unavailable")
                    : lastException;
        });
    }

    public BackendStitchDataSource(Transport transport) {
        this.transport = transport == null ? new HttpApiClient(BackendConfig.defaultConfig()) : transport;
    }

    public String pagePayloadForUrl(String url) throws RemoteAdException {
        String pageName = pageNameFromUrl(url);
        String responseBody = transport.get("/v1/stitch/pages/" + pageName);
        try {
            JSONObject envelope = new JSONObject(responseBody);
            if (!"OK".equals(envelope.optString("code"))) {
                throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, envelope.optString("message"));
            }
            JSONObject data = envelope.optJSONObject("data");
            if (data == null) {
                throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, "Backend response missing data");
            }
            return data.toString();
        } catch (JSONException exception) {
            throw new RemoteAdException(RemoteAdException.Reason.INVALID_RESPONSE, exception.getMessage());
        }
    }

    static String pageNameFromUrl(String url) {
        String assetName = assetNameFromUrl(url);
        if ("search.html".equals(assetName)) {
            return "search";
        }
        if ("messages.html".equals(assetName)) {
            return "messages";
        }
        if ("profile.html".equals(assetName)) {
            return "profile";
        }
        if ("detail.html".equals(assetName)) {
            return "detail";
        }
        return "home";
    }

    private static String assetNameFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }
        int slash = url.lastIndexOf('/');
        String assetName = slash >= 0 ? url.substring(slash + 1) : url;
        int query = assetName.indexOf('?');
        if (query >= 0) {
            assetName = assetName.substring(0, query);
        }
        int fragment = assetName.indexOf('#');
        return fragment >= 0 ? assetName.substring(0, fragment) : assetName;
    }
}
