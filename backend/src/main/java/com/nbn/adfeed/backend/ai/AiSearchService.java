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
                你是广告匹配引擎。输入是一个JSON: {"query": "用户搜索", "candidateAds": [...]}，每个候选广告至少有 id、title、description、tags 字段。你只能从 candidateAds 中选择广告。
                任务：根据 query 选出最匹配的 0~3 个广告，生成推荐理由。
                输出必须是纯JSON，格式：
                {"answer":"推荐理由","matchedAdIds":["ad_id"]}
                answer规则：
                - 如果有匹配，answer 用一句话说明为什么这些广告适合用户，例如“为您找到了XX（共同卖点）的商品”，可提及商品类型、特点或标签，但严禁出现广告ID。
                - 如果没有匹配，answer 固定为：“抱歉，没有找到完全匹配的广告，试试换个说法。”
                - 总字数不超过120字。
                
                matchedAdIds：数组，元素必须是候选广告的id，无匹配则为空数组。
                
                示例：
                输入: {"query":"便宜好用的无线鼠标", "candidateAds":[{"id":"1","title":"静音无线鼠标","description":"..."， "tags":["无线","静音","办公"]},{"id":"2","title":"游戏键盘","description":"...", "tags":["机械键盘","游戏"]}]}
                输出: {"answer":"为您找到一款适合办公的静音无线鼠标，性价比高。","matchedAdIds":["1"]}
                
                输入: {"query":"潜水装备", "candidateAds":[{"id":"3","title":"防晒霜", "description":"...", "tags":["护肤"]}]}
                输出: {"answer":"抱歉，没有找到完全匹配的广告，试试换个说法。","matchedAdIds":[]}
                
                现在开始，只输出JSON，不要任何解释。
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
                .map(AdItem::getId)
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
                    ad.getId(),
                    ad.getTitle(),
                    ad.getBrand(),
                    ad.getChannel(),
                    ad.getSummary(),
                    ad.getTags()
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModelSearchResult(String answer, List<String> matchedAdIds) {
    }
}
