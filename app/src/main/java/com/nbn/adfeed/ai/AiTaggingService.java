package com.nbn.adfeed.ai;

import com.nbn.adfeed.data.model.AdItem;

import java.util.List;

public interface AiTaggingService {
    AiResponse<List<String>> generateTags(AdItem item);
}
