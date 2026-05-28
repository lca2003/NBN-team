package com.nbn.adfeed.data.mock;

import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.InteractionState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class MockAdFixtures {
    private MockAdFixtures() {
    }

    static List<AdItem> createAds() {
        List<AdItem> items = new ArrayList<>();
        items.add(create(
                "ad_001",
                "轻量跑鞋新品首发",
                "NBN Sports",
                "精选",
                "主打轻量缓震和夜跑反光设计，适合校园通勤、夜跑和日常训练。",
                "轻量缓震跑鞋，适合通勤夜跑。",
                "https://example.invalid/images/ad_001.jpg",
                null,
                AdContentType.LARGE_IMAGE,
                "运动",
                "学生党",
                "性价比",
                "通勤"
        ));
        items.add(create(
                "ad_002",
                "周末本地咖啡地图",
                "City Cafe",
                "本地",
                "精选附近适合学习、小组讨论和周末放松的咖啡空间。",
                "本地咖啡空间，适合学习讨论。",
                "https://example.invalid/images/ad_002.jpg",
                null,
                AdContentType.SMALL_IMAGE,
                "本地",
                "生活方式",
                "休闲",
                "学习"
        ));
        items.add(create(
                "ad_003",
                "智能耳机降噪体验",
                "Sound Lab",
                "电商",
                "展示通勤降噪、长续航和低延迟游戏模式的视频广告。",
                "降噪长续航耳机，适合通勤。",
                "https://example.invalid/images/ad_003.jpg",
                "https://example.invalid/videos/ad_003.mp4",
                AdContentType.VIDEO,
                "数码",
                "通勤",
                "视频",
                "降噪"
        ));
        items.add(create(
                "ad_004",
                "宿舍桌面收纳套装",
                "Tiny Space",
                "电商",
                "组合式桌面收纳，适合宿舍书桌、租房办公和小空间整理。",
                "桌面收纳套装，适合宿舍小空间。",
                "https://example.invalid/images/ad_004.jpg",
                null,
                AdContentType.SMALL_IMAGE,
                "宿舍",
                "收纳",
                "学生党",
                "实用"
        ));
        items.add(create(
                "ad_005",
                "城市骑行轻风夹克",
                "Urban Ride",
                "精选",
                "防风轻薄夹克，适合春秋骑行、短途通勤和户外散步。",
                "轻薄防风夹克，适合城市骑行。",
                "https://example.invalid/images/ad_005.jpg",
                null,
                AdContentType.LARGE_IMAGE,
                "骑行",
                "户外",
                "通勤",
                "轻量"
        ));
        items.add(create(
                "ad_006",
                "本地健身体验课",
                "Fit Block",
                "本地",
                "附近小团课体验，覆盖力量训练、体态改善和新手入门指导。",
                "本地健身小团课，适合新手体验。",
                "https://example.invalid/images/ad_006.jpg",
                null,
                AdContentType.SMALL_IMAGE,
                "本地",
                "运动",
                "新手",
                "健康"
        ));
        items.add(create(
                "ad_007",
                "AI 学习助手会员",
                "Study Mate",
                "电商",
                "面向课程复习、资料整理和错题总结的学习助手服务。",
                "AI 学习助手，辅助复习整理。",
                "https://example.invalid/images/ad_007.jpg",
                "https://example.invalid/videos/ad_007.mp4",
                AdContentType.VIDEO,
                "AI",
                "学习",
                "效率",
                "学生党"
        ));
        items.add(create(
                "ad_008",
                "周边周末露营装备",
                "Camp Go",
                "精选",
                "轻量帐篷、折叠椅和便携照明组合，适合周边短途露营。",
                "周末露营装备，轻量便携。",
                "https://example.invalid/images/ad_008.jpg",
                null,
                AdContentType.LARGE_IMAGE,
                "露营",
                "户外",
                "周末",
                "便携"
        ));
        return items;
    }

    private static AdItem create(
            String id,
            String title,
            String brand,
            String channel,
            String description,
            String summary,
            String imageUrl,
            String videoUrl,
            AdContentType contentType,
            String... tags
    ) {
        return new AdItem(
                id,
                title,
                brand,
                channel,
                description,
                summary,
                imageUrl,
                videoUrl,
                contentType,
                Arrays.asList(tags),
                new InteractionState()
        );
    }
}
