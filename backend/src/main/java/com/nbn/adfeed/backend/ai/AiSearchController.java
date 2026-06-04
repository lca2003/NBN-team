package com.nbn.adfeed.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai")
public class AiSearchController {
    private final AiSearchService aiSearchService;

    public AiSearchController(AiSearchService aiSearchService) {
        this.aiSearchService = aiSearchService;
    }

    @PostMapping("/search")
    public AiSearchResponse search(@RequestBody JsonNode requestBody) {
        String query = extractQuery(requestBody);
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank");
        }
        return aiSearchService.search(query);
    }

    private static String extractQuery(JsonNode requestBody) {
        if (requestBody == null || requestBody.isNull()) {
            return "";
        }
        if (requestBody.isTextual()) {
            return requestBody.asText();
        }
        JsonNode query = requestBody.get("query");
        return query == null || query.isNull() ? "" : query.asText();
    }
}
