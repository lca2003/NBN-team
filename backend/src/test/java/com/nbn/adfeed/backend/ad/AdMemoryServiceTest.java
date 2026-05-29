package com.nbn.adfeed.backend.ad;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdMemoryServiceTest {
    private final AdMemoryService service = new AdMemoryService();

    @Test
    void providesAtLeastSixAdsAcrossRequiredChannels() {
        assertThat(service.findAll()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(service.findAll())
                .extracting(AdItem::channel)
                .contains("精选", "电商", "本地");
    }

    @Test
    void returnsReadOnlyAds() {
        assertThatThrownBy(() -> service.findAll().add(new AdItem(
                "x", "title", "brand", "精选", "description", java.util.List.of("tag")
        ))).isInstanceOf(UnsupportedOperationException.class);
    }
}
