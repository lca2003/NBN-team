package com.nbn.adfeed.backend.ai;

import java.util.List;

public record AiSearchResponse(
        String answer,
        List<String> matchedAdIds,
        boolean fallback
) {
}
