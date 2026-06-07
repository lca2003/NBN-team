package com.nbn.backend.domain.commerce;

import com.nbn.backend.store.JsonSeedStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class CommerceApiService {
    private final Map<String, JSONObject> detailsByAdId = new LinkedHashMap<>();
    private final Map<String, JSONObject> merchantsById = new LinkedHashMap<>();

    public CommerceApiService(JsonSeedStore seedStore) {
        JSONArray details = seedStore.documentCopy("ad_details.json").getJSONArray("details");
        for (int index = 0; index < details.length(); index++) {
            JSONObject detail = details.getJSONObject(index);
            detailsByAdId.put(detail.getString("adId"), detail);
            JSONObject merchant = detail.getJSONObject("merchant");
            merchantsById.put(merchant.getString("merchantId"), merchant);
        }
    }

    public synchronized String commerceJson(String adId) {
        JSONObject detail = detailsByAdId.get(adId);
        if (detail == null) {
            return null;
        }
        return new JSONObject()
                .put("adId", adId)
                .put("title", detail.optString("title"))
                .put("merchant", copy(detail.getJSONObject("merchant")))
                .put("offer", copy(detail.getJSONObject("offer")))
                .put("sellingPoints", new JSONArray(detail.getJSONArray("sellingPoints").toString()))
                .toString();
    }

    public synchronized String merchantJson(String merchantId) {
        JSONObject merchant = merchantsById.get(merchantId);
        return merchant == null ? null : "{\"merchant\":" + copy(merchant) + "}";
    }

    public synchronized String nearbyMerchantsJson(String merchantId) {
        if (!merchantsById.containsKey(merchantId)) {
            return null;
        }
        JSONArray items = new JSONArray();
        for (JSONObject merchant : merchantsById.values()) {
            items.put(copy(merchant));
        }
        return new JSONObject()
                .put("merchantId", merchantId)
                .put("items", items)
                .toString();
    }

    public synchronized String checkoutIntentJson(String requestBody) {
        JSONObject body = objectOrEmpty(requestBody);
        String adId = body.optString("adId", "");
        JSONObject detail = detailsByAdId.get(adId);
        JSONObject offer = detail == null ? new JSONObject() : detail.getJSONObject("offer");
        JSONObject data = new JSONObject();
        data.put("checkoutIntentId", body.optString("checkoutIntentId", "checkout_" + UUID.randomUUID()));
        data.put("adId", adId);
        data.put("offerId", body.optString("offerId", offer.optString("offerId", "")));
        data.put("quantity", Math.max(1, body.optInt("quantity", 1)));
        data.put("amountText", offer.optString("priceText", body.optString("amountText", "")));
        data.put("ctaText", offer.optString("ctaText", "继续支付"));
        data.put("status", "CREATED");
        return data.toString();
    }

    private static JSONObject objectOrEmpty(String requestBody) {
        String normalized = requestBody == null ? "" : requestBody.trim();
        return normalized.isEmpty() ? new JSONObject() : new JSONObject(normalized);
    }

    private static JSONObject copy(JSONObject source) {
        return new JSONObject(source.toString());
    }
}
