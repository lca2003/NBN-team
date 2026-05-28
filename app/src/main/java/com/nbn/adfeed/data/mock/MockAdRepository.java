package com.nbn.adfeed.data.mock;

import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.AdPage;
import com.nbn.adfeed.data.model.PageRequest;
import com.nbn.adfeed.data.repository.AdRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MockAdRepository implements AdRepository {
    private static final String ALL_CHANNEL = "全部";

    private final List<AdItem> ads = MockAdFixtures.createAds();

    @Override
    public List<AdItem> getInitialAds() {
        return getAdsPage(PageRequest.firstPage(null, PageRequest.DEFAULT_PAGE_SIZE)).getItems();
    }

    @Override
    public List<AdItem> getAdsByChannel(String channel) {
        return filterByChannel(channel);
    }

    @Override
    public AdPage getAdsPage(PageRequest request) {
        List<AdItem> filtered = filterByChannel(request.getChannel());
        int start = (request.getPageNumber() - 1) * request.getPageSize();
        if (start >= filtered.size()) {
            return new AdPage(new ArrayList<>(), null, false);
        }

        int end = Math.min(start + request.getPageSize(), filtered.size());
        boolean hasMore = end < filtered.size();
        String nextCursor = hasMore ? "page_" + (request.getPageNumber() + 1) : null;
        return new AdPage(filtered.subList(start, end), nextCursor, hasMore);
    }

    @Override
    public AdItem getAdById(String adId) {
        if (adId == null) {
            return null;
        }
        for (AdItem ad : ads) {
            if (ad.getId().equals(adId)) {
                return ad;
            }
        }
        return null;
    }

    @Override
    public List<AdItem> getAdsByTag(String tag) {
        String normalizedTag = normalize(tag);
        List<AdItem> result = new ArrayList<>();
        if (normalizedTag.isEmpty()) {
            return result;
        }
        for (AdItem ad : ads) {
            for (String adTag : ad.getTags()) {
                if (normalize(adTag).equals(normalizedTag)) {
                    result.add(ad);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public List<AdItem> searchByKeyword(String keyword) {
        String normalizedKeyword = normalize(keyword);
        List<AdItem> result = new ArrayList<>();
        if (normalizedKeyword.isEmpty()) {
            return result;
        }
        for (AdItem ad : ads) {
            if (matches(ad, normalizedKeyword)) {
                result.add(ad);
            }
        }
        return result;
    }

    private List<AdItem> filterByChannel(String channel) {
        String normalizedChannel = normalize(channel);
        if (normalizedChannel.isEmpty() || normalize(ALL_CHANNEL).equals(normalizedChannel)) {
            return new ArrayList<>(ads);
        }

        List<AdItem> result = new ArrayList<>();
        for (AdItem ad : ads) {
            if (normalize(ad.getChannel()).equals(normalizedChannel)) {
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
        return normalizedKeyword.contains(normalizedValue) || normalizedValue.contains(normalizedKeyword);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
