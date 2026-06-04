package com.nbn.adfeed.data.mock;

import com.nbn.adfeed.data.model.AdContentType;
import com.nbn.adfeed.data.model.AdItem;
import com.nbn.adfeed.data.model.AdStats;
import com.nbn.adfeed.data.model.InteractionState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class MockAdFixtures {
    private static final String RESOURCE_BASE = "android.resource://com.nbn.adfeed/";

    private MockAdFixtures() {
    }

    static List<AdItem> createAds() {
        List<AdItem> items = new ArrayList<>();
        items.add(create("ad_001", "轻量跑鞋新品首发", "NBN Sports", "精选", "精选",
                "主打轻量缓震和夜跑反光设计，适合校园通勤、夜跑和日常训练。",
                "轻量缓震跑鞋，适合通勤夜跑。", AdContentType.LARGE_IMAGE, 1680, 268, 320, 180, 46,
                "运动", "学生党", "性价比", "通勤"));
        items.add(create("ad_002", "周末本地咖啡地图", "City Cafe", "本地", "本地",
                "精选附近适合学习、小组讨论和周末放松的咖啡空间。",
                "本地咖啡空间，适合学习讨论。", AdContentType.SMALL_IMAGE, 890, 120, 88, 76, 19,
                "本地", "咖啡", "学习", "休闲"));
        items.add(create("ad_003", "智能耳机降噪体验", "Sound Lab", "电商", "电商",
                "展示通勤降噪、长续航和低延迟游戏模式的视频广告。",
                "降噪长续航耳机，适合通勤。", AdContentType.VIDEO, 2200, 410, 390, 260, 81,
                "数码", "通勤", "视频", "降噪"));
        items.add(create("ad_004", "宿舍桌面收纳套装", "Tiny Space", "电商", "电商",
                "组合式桌面收纳，适合宿舍书桌、租房办公和小空间整理。",
                "桌面收纳套装，适合宿舍小空间。", AdContentType.SMALL_IMAGE, 760, 93, 102, 88, 14,
                "宿舍", "收纳", "学生党", "实用"));
        items.add(create("ad_005", "城市骑行轻风夹克", "Urban Ride", "精选", "精选",
                "防风轻薄夹克，适合春秋骑行、短途通勤和户外散步。",
                "轻薄防风夹克，适合城市骑行。", AdContentType.LARGE_IMAGE, 1420, 205, 160, 101, 33,
                "骑行", "户外", "通勤", "轻量"));
        items.add(create("ad_006", "本地健身体验课", "Fit Block", "本地", "本地",
                "附近小团课体验，覆盖力量训练、体态改善和新手入门指导。",
                "本地健身小团课，适合新手体验。", AdContentType.SMALL_IMAGE, 640, 72, 62, 39, 8,
                "本地", "运动", "新手", "健康"));
        items.add(create("ad_007", "AI 学习助手会员", "Study Mate", "电商", "电商",
                "面向课程复习、资料整理和错题总结的学习助手服务。",
                "AI 学习助手，辅助复习整理。", AdContentType.VIDEO, 1880, 340, 310, 206, 64,
                "AI", "学习", "效率", "学生党"));
        items.add(create("ad_008", "周边周末露营装备", "Camp Go", "精选", "精选",
                "轻量帐篷、折叠椅和便携照明组合，适合周边短途露营。",
                "周末露营装备，轻量便携。", AdContentType.LARGE_IMAGE, 1180, 176, 132, 91, 22,
                "露营", "户外", "周末", "便携"));
        items.add(create("ad_009", "通勤双肩包防泼水", "Metro Bag", "电商", "电商",
                "分层收纳电脑、雨伞和水杯，适合上课、实习和日常通勤。",
                "防泼水双肩包，适合上课通勤。", AdContentType.LARGE_IMAGE, 1320, 188, 170, 136, 31,
                "通勤", "学生党", "数码", "收纳"));
        items.add(create("ad_010", "社区早餐新店开业", "Morning Hub", "本地", "本地",
                "提供热饮、三明治和简餐，适合早八、上班和周末早午餐。",
                "社区早餐新店，适合早八通勤。", AdContentType.SMALL_IMAGE, 510, 64, 41, 35, 6,
                "本地", "早餐", "通勤", "生活"));
        items.add(create("ad_011", "运动水杯夏季轻量款", "Hydro Way", "精选", "精选",
                "大容量但便携，适合跑步、骑行、健身和日常校园携带。",
                "轻量运动水杯，适合校园健身。", AdContentType.SMALL_IMAGE, 940, 130, 110, 82, 17,
                "运动", "校园", "轻量", "户外"));
        items.add(create("ad_012", "咖啡豆新鲜烘焙订阅", "Bean Lab", "电商", "电商",
                "每周小批量烘焙寄送，适合宿舍手冲、办公室分享和周末慢生活。",
                "新鲜烘焙咖啡豆，适合手冲。", AdContentType.LARGE_IMAGE, 830, 104, 74, 67, 13,
                "咖啡", "电商", "生活方式", "周末"));
        items.add(create("ad_013", "图书馆周边自习空间", "Quiet Room", "本地", "本地",
                "安静座位、插座和储物柜，适合期末复习、小组讨论和考研自习。",
                "本地自习空间，适合复习讨论。", AdContentType.LARGE_IMAGE, 1230, 151, 119, 96, 24,
                "本地", "学习", "学生党", "安静"));
        items.add(create("ad_014", "多场景轻量装备：学生党通勤健身露营组合推荐", "Urban Kit", "精选", "精选",
                "轻量装备组合覆盖学生党、通勤、运动、露营和数码收纳等多个场景，适合需要一套解决多种日常任务的人群。",
                "多场景轻量装备组合推荐。", AdContentType.LARGE_IMAGE, 1520, 219, 141, 105, 27,
                "长标题", "学生党", "通勤", "运动", "露营"));
        items.add(create("ad_015", "便携蓝牙音箱户外版", "Sound Lab", "电商", "电商",
                "小体积、防泼水、低延迟，适合露营、骑行休息和宿舍影音。",
                "便携蓝牙音箱，适合户外宿舍。", AdContentType.VIDEO, 1750, 286, 250, 183, 58,
                "数码", "露营", "视频", "户外"));
        items.add(create("ad_016", "商圈午餐优惠合集", "Lunch Map", "本地", "本地",
                "围绕学校和办公区整理的午餐选择，支持快取和多人拼单。",
                "本地午餐合集，适合快取拼单。", AdContentType.SMALL_IMAGE, 700, 91, 55, 46, 9,
                "本地", "午餐", "通勤", "优惠"));
        items.add(create("ad_017", "学生党平价护眼台灯", "Bright Desk", "电商", "电商",
                "", "护眼台灯，适合宿舍学习。", AdContentType.SMALL_IMAGE, 1010, 140, 134, 112, 21,
                "学生党", "宿舍", "学习", "数码"));
        items.add(create("ad_018", "城市夜跑路线推荐", "Run City", "精选", "精选",
                "精选安全照明、补给便利和风景较好的城市夜跑路线。",
                "城市夜跑路线，适合下班运动。", AdContentType.VIDEO, 1360, 198, 148, 96, 38,
                "运动", "夜跑", "本地", "视频"));
        items.add(create("ad_019", "社区宠物友好咖啡馆", "Paw Cafe", "本地", "本地",
                "可携宠、轻食、露台座位，适合周末放松和朋友聚会。",
                "宠物友好咖啡馆，适合周末聚会。", AdContentType.LARGE_IMAGE, 920, 107, 80, 66, 16,
                "本地", "咖啡", "宠物", "周末"));
        items.add(create("ad_020", "轻薄平板键盘套", "Key Dock", "电商", "电商",
                "兼顾课程笔记、移动办公和短途出差，支持多角度支架。",
                "平板键盘套，适合移动学习办公。", AdContentType.SMALL_IMAGE, 1580, 238, 201, 164, 42,
                "数码", "学习", "通勤", "效率"));
        items.add(create("ad_021", "城市徒步补给包", "Walk Kit", "精选", "精选",
                "包含便携雨衣、能量小食和小型急救包，适合城市徒步和短途探索。",
                "城市徒步补给包，轻便实用。", AdContentType.SMALL_IMAGE, 610, 66, 44, 31, 5,
                "徒步", "户外", "通勤", "轻量"));
        items.add(create("ad_022", "附近篮球馆夜场", "Hoop Zone", "本地", "本地",
                "工作日夜场和周末拼场信息，适合同学组队和下班运动。",
                "附近篮球夜场，适合组队运动。", AdContentType.VIDEO, 1090, 155, 121, 87, 23,
                "本地", "运动", "篮球", "视频"));
        items.add(create("ad_023", "咖啡通勤保温杯", "Bean Bottle", "电商", "电商",
                "适合早八咖啡、办公室热饮和短途出行，杯身易清洗。",
                "通勤保温杯，适合咖啡热饮。", AdContentType.SMALL_IMAGE, 860, 116, 92, 73, 15,
                "咖啡", "通勤", "电商", "生活"));
        items.add(create("ad_024", "校园社团招新物料", "Poster Kit", "精选", "精选",
                "海报、贴纸、桌牌和社媒图模板，适合社团招新和活动宣传。",
                "社团招新物料，适合活动宣传。", AdContentType.LARGE_IMAGE, 590, 59, 40, 28, 4,
                "校园", "学生党", "设计", "活动"));
        items.add(create("ad_025", "本地周末市集指南", "Market Walk", "本地", "本地",
                "手作、咖啡、轻食和户外摊位集合，适合周末拍照和朋友同行。",
                "周末市集指南，覆盖咖啡轻食。", AdContentType.LARGE_IMAGE, 1120, 133, 100, 81, 18,
                "本地", "咖啡", "周末", "生活"));
        items.add(create("ad_026", "运动恢复筋膜球", "Fit Block", "电商", "电商",
                "训练后放松工具，适合跑步、健身和久坐通勤后的肌肉放松。",
                "运动恢复工具，适合训练后放松。", AdContentType.SMALL_IMAGE, 980, 127, 96, 71, 12,
                "运动", "健身", "通勤", "健康"));
        items.add(create("ad_027", "城市光影摄影课", "Lens Local", "本地", "本地",
                "线下小班课程，覆盖手机摄影、夜景构图和短视频拍摄。",
                "本地摄影课，覆盖夜景和短视频。", AdContentType.VIDEO, 760, 88, 70, 52, 11,
                "本地", "摄影", "视频", "学习"));
        items.add(create("ad_028", "学生党数码配件包", "Cable Box", "电商", "电商",
                "收纳充电线、转接头、U 盘和耳机，适合宿舍、图书馆和通勤。",
                "数码配件包，适合学生通勤。", AdContentType.LARGE_IMAGE, 1460, 230, 210, 170, 36,
                "学生党", "数码", "通勤", "收纳"));
        items.add(create("ad_029", "精选通勤雨伞轻量版", "Rain Go", "精选", "精选",
                "小包可放、开合顺手，适合梅雨季通勤和校园日常。",
                "轻量雨伞，适合通勤校园。", AdContentType.SMALL_IMAGE, 680, 79, 52, 36, 7,
                "通勤", "校园", "轻量", "生活"));
        items.add(create("ad_030", "空描述规则降级广告", "Fallback Lab", "精选", "精选",
                "", "", AdContentType.LARGE_IMAGE, 430, 38, 22, 17, 3,
                "降级", "AI", "测试", "精选"));
        return items;
    }

    private static AdItem create(
            String id,
            String title,
            String brand,
            String channel,
            String channelId,
            String description,
            String summary,
            AdContentType contentType,
            int exposures,
            int clicks,
            int likes,
            int collects,
            int shares,
            String... tags
    ) {
        String suffix = id.substring(id.indexOf('_') + 1);
        String imageUrl = drawableUri(imageResourceFor(id));
        String thumbnailUrl = drawableUri(thumbnailResourceFor(id));
        String videoUrl = contentType == AdContentType.VIDEO ? rawUri(videoResourceFor(id)) : null;
        return new AdItem(
                id,
                title,
                brand,
                channel,
                channelId,
                description,
                summary,
                imageUrl,
                thumbnailUrl,
                videoUrl,
                commerceOfferFor(id),
                commerceCtaFor(id),
                contentType,
                Arrays.asList(tags),
                new InteractionState(false, false),
                new AdStats(exposures, clicks, likes, collects, shares),
                "mock_" + suffix
        );
    }

    private static String commerceOfferFor(String id) {
        switch (id) {
            case "ad_003":
                return "¥699 · 618 到手价 · 24 期免息";
            case "ad_004":
                return "¥79 · 第二件 8 折 · 宿舍收纳套装";
            case "ad_007":
                return "¥19/月 · 学生认证 5 折 · 7 天试用";
            case "ad_009":
                return "¥159 · 限时立减 30 · 防泼水通勤包";
            case "ad_012":
                return "¥49/周 · 首单减 10 · 新鲜烘焙订阅";
            case "ad_015":
                return "¥129 · 户外套装价 · 露营音箱";
            case "ad_017":
                return "¥59 · 学生价 · 3 档护眼";
            case "ad_020":
                return "¥199 · 键盘套组合价 · 送保护膜";
            case "ad_023":
                return "¥69 · 通勤杯限时价 · 赠清洁刷";
            case "ad_026":
                return "¥39 · 运动恢复组合 · 入门装";
            case "ad_028":
                return "¥89 · 学生套装价 · 线材全收纳";
            default:
                return "";
        }
    }

    private static String commerceCtaFor(String id) {
        return commerceOfferFor(id).isEmpty() ? "" : "查看商品";
    }

    private static String drawableUri(String resourceName) {
        return RESOURCE_BASE + "drawable/" + resourceName;
    }

    private static String rawUri(String resourceName) {
        return RESOURCE_BASE + "raw/" + resourceName;
    }

    private static String imageResourceFor(String id) {
        switch (id) {
            case "ad_002":
            case "ad_010":
            case "ad_016":
            case "ad_023":
                return "ad_media_small_cafe";
            case "ad_004":
            case "ad_017":
            case "ad_021":
            case "ad_029":
                return "ad_media_small_lifestyle";
            case "ad_006":
            case "ad_011":
            case "ad_026":
                return "ad_media_small_fitness";
            case "ad_020":
                return "ad_media_small_tech";
            case "ad_003":
            case "ad_015":
                return "ad_media_video_headphones";
            case "ad_007":
            case "ad_027":
                return "ad_media_video_study";
            case "ad_018":
            case "ad_022":
                return "ad_media_video_local_sports";
            case "ad_005":
            case "ad_008":
                return "ad_media_large_outdoor";
            case "ad_012":
            case "ad_019":
            case "ad_025":
                return "ad_media_large_market";
            case "ad_013":
            case "ad_024":
            case "ad_030":
                return "ad_media_large_study";
            case "ad_009":
            case "ad_028":
                return "ad_media_large_tech";
            case "ad_001":
            case "ad_014":
            default:
                return "ad_media_large_sports";
        }
    }

    private static String thumbnailResourceFor(String id) {
        switch (id) {
            case "ad_003":
            case "ad_015":
                return "ad_media_video_headphones";
            case "ad_007":
            case "ad_027":
                return "ad_media_video_study";
            case "ad_018":
            case "ad_022":
                return "ad_media_video_local_sports";
            default:
                return imageResourceFor(id);
        }
    }

    private static String videoResourceFor(String id) {
        switch (id) {
            case "ad_003":
            case "ad_015":
                return "ad_video_headphones";
            case "ad_007":
            case "ad_027":
                return "ad_video_study_ai";
            case "ad_018":
            case "ad_022":
                return "ad_video_local_sports";
            default:
                return "ad_video_headphones";
        }
    }
}
