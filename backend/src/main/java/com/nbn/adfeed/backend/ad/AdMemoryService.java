package com.nbn.adfeed.backend.ad;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdMemoryService {
    private final List<AdItem> ads;

    public AdMemoryService() {
//        this.ads = List.of(
//                new AdItem(
//                        "ad_001",
//                        "轻盈缓震跑鞋",
//                        "StrideNow",
//                        "精选",
//                        "适合校园通勤和日常训练的高性价比跑鞋。",
//                        List.of("运动", "通勤", "学生党", "性价比")
//                ),
//                new AdItem(
//                        "ad_002",
//                        "多档便携露营灯",
//                        "CampBeam",
//                        "精选",
//                        "支持夜间户外照明的小巧露营灯。",
//                        List.of("露营", "便携", "夜间", "户外")
//                ),
//                new AdItem(
//                        "ad_003",
//                        "宿舍桌面收纳套装",
//                        "NeatBox",
//                        "电商",
//                        "帮助整理宿舍桌面、书架和小物件的收纳组合。",
//                        List.of("收纳", "宿舍", "学生党", "桌面")
//                ),
//                new AdItem(
//                        "ad_004",
//                        "长续航蓝牙耳机",
//                        "SoundPocket",
//                        "电商",
//                        "适合通勤、网课和日常使用的平价蓝牙耳机。",
//                        List.of("耳机", "通勤", "网课", "平价")
//                ),
//                new AdItem(
//                        "ad_005",
//                        "健身房体验课",
//                        "FitLocal",
//                        "本地",
//                        "面向本地新用户的基础健身体验课程。",
//                        List.of("健身", "本地", "体验课", "运动")
//                ),
//                new AdItem(
//                        "ad_006",
//                        "校园打印优惠",
//                        "PrintMate",
//                        "本地",
//                        "覆盖讲义、论文和海报打印的校园优惠活动。",
//                        List.of("校园", "打印", "学生党", "优惠")
//                )
//        );
        this.ads = List.copyOf(createAds());
    }

    public List<AdItem> findAll() {
        return ads;
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
        items.add(new AdItem(
                "ad_004",
                "周末短途露营装备推荐",
                "Outdoor Master",
                "生活",
                "轻便帐篷、折叠桌椅一站式购齐。",
                AdContentType.LARGE_IMAGE,
                Arrays.asList("露营", "户外", "周末"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_005",
                "在线英语一对一限时特惠",
                "SpeakEasy",
                "教育",
                "每天25分钟，快速提升口语流利度。",
                AdContentType.VIDEO,
                Arrays.asList("教育", "英语", "特惠"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_006",
                "扫地机器人智能避障",
                "RoboClean",
                "电商",
                "激光导航＋全屋自动回充。",
                AdContentType.SMALL_IMAGE,
                Arrays.asList("智能家居", "清洁", "科技"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_007",
                "周末影城新片速递",
                "Star Cinema",
                "娱乐",
                "本周热映《流浪地球3》IMAX场次。",
                AdContentType.LARGE_IMAGE,
                Arrays.asList("电影", "娱乐", "周末"),
                new InteractionState()
        ));
        items.add(new AdItem(
                "ad_008",
                "智能手表健康监测",
                "HealthTech",
                "数码",
                "心率血氧睡眠分析，运动必备。",
                AdContentType.SMALL_IMAGE,
                Arrays.asList("穿戴", "健康", "运动"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_009",
                "夏日防晒霜热卖中",
                "SkinCare+",
                "美妆",
                "SPF50+清爽不油腻，户外防晒必备。",
                AdContentType.LARGE_IMAGE,
                Arrays.asList("美妆", "夏日", "护肤"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_010",
                "新能源汽车试驾预约",
                "Green Motor",
                "汽车",
                "零百加速3.8秒，续航700km。",
                AdContentType.VIDEO,
                Arrays.asList("新能源", "汽车", "试驾"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_011",
                "每日瑜伽在线课程",
                "Yoga Flow",
                "健康",
                "零基础到高阶，每天15分钟。",
                AdContentType.VIDEO,
                Arrays.asList("瑜伽", "健身", "减压"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_012",
                "无糖气泡水新品上市",
                "BubblePop",
                "食品",
                "0糖0卡0脂，多种水果口味。",
                AdContentType.SMALL_IMAGE,
                Arrays.asList("饮料", "健康", "夏日"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_013",
                "游戏笔记本电脑限时秒杀",
                "GameMax",
                "电商",
                "RTX 4060 + 240Hz高刷屏。",
                AdContentType.LARGE_IMAGE,
                Arrays.asList("游戏", "数码", "秒杀"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_014",
                "周末亲子动物园套票",
                "Happy Zoo",
                "亲子",
                "2大1小仅需99元，寓教于乐。",
                AdContentType.SMALL_IMAGE,
                Arrays.asList("亲子", "周末", "优惠"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_015",
                "智能门锁3D人脸识别",
                "Secure Home",
                "家居",
                "安全便捷，防撬报警。",
                AdContentType.VIDEO,
                Arrays.asList("智能家居", "安全", "科技"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_016",
                "高端商务男装定制",
                "Elite Suit",
                "时尚",
                "意大利面料，免费上门量体。",
                AdContentType.LARGE_IMAGE,
                Arrays.asList("男装", "商务", "定制"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_017",
                "线上编程训练营",
                "CodeCamp",
                "教育",
                "从入门到实战，大厂导师亲授。",
                AdContentType.VIDEO,
                Arrays.asList("编程", "教育", "就业"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_018",
                "宠物智能喂食器",
                "PetPal",
                "宠物",
                "远程控制，定时出粮，防卡粮设计。",
                AdContentType.SMALL_IMAGE,
                Arrays.asList("宠物", "智能", "便利"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_019",
                "进口红酒礼盒装",
                "Vino Select",
                "酒水",
                "法国原瓶进口，送礼佳品。",
                AdContentType.LARGE_IMAGE,
                Arrays.asList("红酒", "送礼", "酒水"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_020",
                "家用椭圆机健身",
                "FitHome",
                "运动",
                "静音磁控，可折叠收纳。",
                AdContentType.VIDEO,
                Arrays.asList("健身", "居家", "运动器材"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_021",
                "电子书阅读器墨水屏",
                "ReadBook",
                "数码",
                "护眼不伤眼，海量书库。",
                AdContentType.SMALL_IMAGE,
                Arrays.asList("阅读", "数码", "学习"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_022",
                "速溶咖啡囤货装",
                "Morning Coffee",
                "食品",
                "精选阿拉比卡豆，冷热双泡。",
                AdContentType.LARGE_IMAGE,
                Arrays.asList("咖啡", "早餐", "囤货"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_023",
                "婚纱摄影旅拍套餐",
                "Sweet Memory",
                "婚庆",
                "三亚/丽江/大理，专业团队跟拍。",
                AdContentType.VIDEO,
                Arrays.asList("婚纱", "旅拍", "婚礼"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_024",
                "家用洗碗机大容量",
                "KitchenAid",
                "家电",
                "13套大容量，热风烘干。",
                AdContentType.SMALL_IMAGE,
                Arrays.asList("家电", "厨房", "懒人"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_025",
                "户外登山杖铝合金",
                "Trail Gear",
                "户外",
                "超轻可折叠，减震防滑。",
                AdContentType.LARGE_IMAGE,
                Arrays.asList("户外", "登山", "装备"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_026",
                "在线心理咨询平台",
                "MindCare",
                "健康",
                "7x24小时专业咨询师陪伴。",
                AdContentType.VIDEO,
                Arrays.asList("心理", "健康", "减压"),
                new InteractionState()
        ));

        items.add(new AdItem(
                "ad_027",
                "电动牙刷情侣款",
                "SmileBright",
                "个护",
                "声波震动，5种清洁模式。",
                AdContentType.SMALL_IMAGE,
                Arrays.asList("个护", "情侣", "口腔"),
                new InteractionState()
        ));
        return items;
    }

}
