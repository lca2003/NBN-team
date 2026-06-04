package com.nbn.adfeed.data.repository;

import static org.junit.Assert.assertSame;

import org.junit.Test;

public final class AppRepositoryProviderTest {
    @Test
    public void getAdRepository_returnsSharedInstance() {
        AdRepository first = AppRepositoryProvider.getAdRepository();
        AdRepository second = AppRepositoryProvider.getAdRepository();

        assertSame(first, second);
    }
}
