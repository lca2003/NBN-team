package com.nbn.adfeed.data.repository;

import com.nbn.adfeed.data.mock.MockAdRepository;

/**
 * 应用级数据源入口。
 *
 * <p>Activity 不直接 new Repository，统一从这里获取，便于后续把 Mock 数据源替换为远程或缓存实现。</p>
 */
public final class AppRepositoryProvider {
    private static final AdRepository AD_REPOSITORY = new MockAdRepository();

    private AppRepositoryProvider() {
    }

    public static AdRepository getAdRepository() {
        return AD_REPOSITORY;
    }
}
