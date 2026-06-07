package com.nbn.adfeed.ai;

import com.nbn.adfeed.data.model.AdItem;

public interface AiSummaryService {
    AiResponse<String> summarize(AdItem item);
}
