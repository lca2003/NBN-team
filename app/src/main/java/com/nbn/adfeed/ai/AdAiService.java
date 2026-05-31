package com.nbn.adfeed.ai;

import java.util.List;

public interface AdAiService {
    AiResponse<String> getAiSummary(String adId);

    AiResponse<List<String>> getAiTags(String adId);
}
