package com.nbn.adfeed.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiSearchController.class)
class AiSearchControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiSearchService aiSearchService;

    @Test
    void forwardsUserQueryToSearchService() throws Exception {
        when(aiSearchService.search("student sports ads"))
                .thenReturn(new AiSearchResponse("model answer", List.of(), false));

        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"student sports ads\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("model answer"))
                .andExpect(jsonPath("$.fallback").value(false));

        verify(aiSearchService).search("student sports ads");
    }

    @Test
    void rejectsBlankQuery() throws Exception {
        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("query must not be blank"));
    }
}
