package com.nbn.adfeed.ai.tagging;

import com.nbn.adfeed.ai.AiGenerationException;
import com.nbn.adfeed.data.model.AdItem;

import java.util.List;

public interface TagGenerator {
    List<String> generateTags(AdItem item) throws AiGenerationException;
}
