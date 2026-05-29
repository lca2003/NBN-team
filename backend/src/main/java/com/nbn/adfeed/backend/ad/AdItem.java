package com.nbn.adfeed.backend.ad;

import java.util.List;

public record AdItem(
        String id,
        String title,
        String brand,
        String channel,
        String description,
        List<String> tags
) {
}
