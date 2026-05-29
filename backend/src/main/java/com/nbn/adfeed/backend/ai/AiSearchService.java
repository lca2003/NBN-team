package com.nbn.adfeed.backend.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbn.adfeed.backend.ad.AdItem;
import com.nbn.adfeed.backend.ad.AdMemoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AiSearchService {
    private static final String UNCONFIGURED_API_KEY = "not-configured";
    private static final String SYSTEM_PROMPT = """
                    你是广告搜索匹配引擎。
                    用户消息是 JSON 格式，包含：
                    - query: 用户的搜索请求
                    - candidateAds: 仅可从这些候选广告中返回结果
            
                    请根据 query 匹配零个或多个候选广告。
                    只能使用 candidateAds 中出现的 id。
                    
                    answer 字段必须是对用户可见的推荐理由（简体中文，不超过 120 字）。
                    重要：不要在 answer 中输出广告 ID（如 “ad_001”），只使用自然语言描述，可引用广告的受众、场景、产品类型、品牌、描述或标签。
                    如果选择了多个广告，请总结它们的共同推荐理由，并提及 1 到 3 个具体的匹配点（例如 “高性价比运动鞋” 而不是 “ad_001”）。
                    
                    只返回合法的 JSON，不要返回 markdown 或代码块。
                    严格按以下 schema 返回：
                    {"answer":"简短的中文推荐理由（不含ID）","matchedAdIds":["ad_001"]}
                    如果没有匹配的候选广告，返回空的 matchedAdIds 数组。
                    """;

    private final String dashScopeApiKey;
    private final ChatClient chatClient;
    private final AdMemoryService adMemoryService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AiSearchService(
            ChatClient.Builder chatClientBuilder,
            AdMemoryService adMemoryService,
            ObjectMapper objectMapper,
            @Value("${spring.ai.dashscope.api-key:not-configured}") String dashScopeApiKey
    ) {
        this(chatClientBuilder.build(), dashScopeApiKey, adMemoryService, objectMapper);
    }

    AiSearchService(
            ChatClient chatClient,
            String dashScopeApiKey,
            AdMemoryService adMemoryService,
            ObjectMapper objectMapper
    ) {
        this.dashScopeApiKey = dashScopeApiKey;
        this.chatClient = chatClient;
        this.adMemoryService = adMemoryService;
        this.objectMapper = objectMapper;
    }

    public AiSearchResponse search(String query) {
        if (!hasConfiguredApiKey()) {
            return fallback(query);
        }

        try {
            String modelOutput = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(query))
                    .call()
                    .content();
            if (modelOutput == null || modelOutput.isBlank()) {
                return fallback(query);
            }
            return parseModelOutput(modelOutput, query);
        } catch (RuntimeException | JsonProcessingException ex) {
            return fallback(query);
        }
    }

    private String buildUserPrompt(String query) throws JsonProcessingException {
        List<CandidateAd> candidateAds = adMemoryService.findAll()
                .stream()
                .map(CandidateAd::from)
                .toList();
        return objectMapper.writeValueAsString(new ModelSearchRequest(query, candidateAds));
    }

    private AiSearchResponse parseModelOutput(String modelOutput, String query) throws JsonProcessingException {
        ModelSearchResult result = objectMapper.readValue(modelOutput, ModelSearchResult.class);
        if (result.answer() == null || result.answer().isBlank()) {
            return fallback(query);
        }

        Set<String> validIds = adMemoryService.findAll()
                .stream()
                .map(AdItem::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> matchedIds = result.matchedAdIds() == null
                ? List.of()
                : result.matchedAdIds()
                .stream()
                .filter(validIds::contains)
                .distinct()
                .toList();

        return new AiSearchResponse(result.answer().trim(), matchedIds, false);
    }

    private boolean hasConfiguredApiKey() {
        return dashScopeApiKey != null
                && !dashScopeApiKey.isBlank()
                && !UNCONFIGURED_API_KEY.equals(dashScopeApiKey);
    }

    private AiSearchResponse fallback(String query) {
        return new AiSearchResponse("Local placeholder reply: " + query, List.of(), true);
    }

    private record ModelSearchRequest(String query, List<CandidateAd> candidateAds) {
    }




    private record CandidateAd(
            String id,
            String title,
            String brand,
            String channel,
            String description,
            List<String> tags
    ) {
        private static CandidateAd from(AdItem ad) {
            return new CandidateAd(
                    ad.id(),
                    ad.title(),
                    ad.brand(),
                    ad.channel(),
                    ad.description(),
                    ad.tags()
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModelSearchResult(String answer, List<String> matchedAdIds) {
    }
}
