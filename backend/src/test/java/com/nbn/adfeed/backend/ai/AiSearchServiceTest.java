package com.nbn.adfeed.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbn.adfeed.backend.ad.AdMemoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiSearchServiceTest {
    @Test
    void sendsQueryAndCandidateAdsToChatClientAndReturnsKnownMatchedIds() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
                {"answer":"matched ads","matchedAdIds":["ad_001","missing_ad"]}
                """);
        AiSearchService service = new AiSearchService(
                chatClient,
                "real-api-key",
                new AdMemoryService(),
                new ObjectMapper()
        );

        AiSearchResponse response = service.search("student sports ads");

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemPrompt.capture());
        verify(requestSpec).user(userPrompt.capture());
        assertThat(systemPrompt.getValue())
                .contains("Return only valid JSON")
                .contains("matchedAdIds")
                .contains("recommendation reason")
                .contains("selected ads");
        assertThat(userPrompt.getValue())
                .contains("student sports ads")
                .contains("candidateAds")
                .contains("ad_001")
                .contains("StrideNow");
        assertThat(response.answer()).isEqualTo("matched ads");
        assertThat(response.matchedAdIds()).containsExactly("ad_001");
        assertThat(response.fallback()).isFalse();
    }

    @Test
    void fallsBackWhenModelOutputIsNotJson() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("plain text");
        AiSearchService service = new AiSearchService(
                chatClient,
                "real-api-key",
                new AdMemoryService(),
                new ObjectMapper()
        );

        AiSearchResponse response = service.search("student sports ads");

        assertThat(response.answer()).contains("student sports ads");
        assertThat(response.matchedAdIds()).isEmpty();
        assertThat(response.fallback()).isTrue();
    }

    @Test
    void usesLocalFallbackWhenApiKeyIsNotConfigured() {
        ChatClient chatClient = mock(ChatClient.class);
        AiSearchService service = new AiSearchService(
                chatClient,
                "not-configured",
                new AdMemoryService(),
                new ObjectMapper()
        );

        AiSearchResponse response = service.search("student sports ads");

        verifyNoInteractions(chatClient);
        assertThat(response.answer()).contains("student sports ads");
        assertThat(response.fallback()).isTrue();
    }
}
