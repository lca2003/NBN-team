package com.nbn.adfeed.ai;

import com.nbn.adfeed.data.model.AdItem;

import java.util.List;

public interface AiSummaryService {
    String summarize(AdItem item);

    List<String> generateTags(AdItem item);
}
