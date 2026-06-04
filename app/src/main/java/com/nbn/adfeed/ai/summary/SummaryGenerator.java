package com.nbn.adfeed.ai.summary;

import com.nbn.adfeed.ai.AiGenerationException;
import com.nbn.adfeed.data.model.AdItem;

public interface SummaryGenerator {
    String generateSummary(AdItem item) throws AiGenerationException;
}
