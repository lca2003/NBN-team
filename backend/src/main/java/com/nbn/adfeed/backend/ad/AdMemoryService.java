package com.nbn.adfeed.backend.ad;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdMemoryService {
    private final List<AdItem> ads;

    public AdMemoryService() {
        this.ads = List.of(
                new AdItem(
                        "ad_001",
                        "轻盈缓震跑鞋",
                        "StrideNow",
                        "精选",
                        "适合校园通勤和日常训练的高性价比跑鞋。",
                        List.of("运动", "通勤", "学生党", "性价比")
                ),
                new AdItem(
                        "ad_002",
                        "多档便携露营灯",
                        "CampBeam",
                        "精选",
                        "支持夜间户外照明的小巧露营灯。",
                        List.of("露营", "便携", "夜间", "户外")
                ),
                new AdItem(
                        "ad_003",
                        "宿舍桌面收纳套装",
                        "NeatBox",
                        "电商",
                        "帮助整理宿舍桌面、书架和小物件的收纳组合。",
                        List.of("收纳", "宿舍", "学生党", "桌面")
                ),
                new AdItem(
                        "ad_004",
                        "长续航蓝牙耳机",
                        "SoundPocket",
                        "电商",
                        "适合通勤、网课和日常使用的平价蓝牙耳机。",
                        List.of("耳机", "通勤", "网课", "平价")
                ),
                new AdItem(
                        "ad_005",
                        "健身房体验课",
                        "FitLocal",
                        "本地",
                        "面向本地新用户的基础健身体验课程。",
                        List.of("健身", "本地", "体验课", "运动")
                ),
                new AdItem(
                        "ad_006",
                        "校园打印优惠",
                        "PrintMate",
                        "本地",
                        "覆盖讲义、论文和海报打印的校园优惠活动。",
                        List.of("校园", "打印", "学生党", "优惠")
                )
        );
    }

    public List<AdItem> findAll() {
        return ads;
    }
}
