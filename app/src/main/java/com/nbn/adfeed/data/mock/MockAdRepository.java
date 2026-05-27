package com.nbn.adfeed.data.mock;

import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionState;
import com.nbn.adfeed.data.repository.AdRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class MockAdRepository implements AdRepository {
    private final List<AdItem> ads = createAds();

    @Override
    public List<AdItem> getInitialAds() {
        return new ArrayList<>(ads);
    }

    @Override
    public List<AdItem> getAdsByChannel(String channel) {
        List<AdItem> result = new ArrayList<>();
        for (AdItem ad : ads) {
            if (ad.getChannel().equals(channel)) {
                result.add(ad);
            }
        }
        return result;
    }

    @Override
    public List<AdItem> searchByKeyword(String keyword) {
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        List<AdItem> result = new ArrayList<>();
        for (AdItem ad : ads) {
            String target = (ad.getTitle() + " " + ad.getBrand() + " " + ad.getSummary())
                    .toLowerCase(Locale.ROOT);
            if (target.contains(normalizedKeyword)) {
                result.add(ad);
            }
        }
        return result;
    }

    private static List<AdItem> createAds() {
        List<AdItem> items = new ArrayList<>();
        items.add(new AdItem(
                "ad_001",
                "轻量跑鞋新品首发",
                "NBN Sports",
                "精选",
                "适合通勤和夜跑的轻量缓震跑鞋。",
                AdContentType.LARGE_IMAGE,
                Arrays.asList("运动", "学生党", "性价比"),
                new InteractionState()
        ));
        items.add(new AdItem(
                "ad_002",
                "周末本地咖啡地图",
                "City Cafe",
                "本地",
                "发现附近适合学习和小组讨论的咖啡空间。",
                AdContentType.SMALL_IMAGE,
                Arrays.asList("本地", "生活方式", "休闲"),
                new InteractionState()
        ));
        items.add(new AdItem(
                "ad_003",
                "智能耳机降噪体验",
                "Sound Lab",
                "电商",
                "主打通勤降噪和长续航的视频广告。",
                AdContentType.VIDEO,
                Arrays.asList("数码", "通勤", "视频"),
                new InteractionState()
        ));
        return items;
    }
}
