package com.example.kirotest.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.kirotest.dto.request.AskRequest;
import com.example.kirotest.dto.response.AskResponse;
import com.example.kirotest.exception.GlobalExceptionHandler;
import com.example.kirotest.exception.LlmException;
import com.example.kirotest.service.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code @WebMvcTest} slice tests for {@link AiController}.
 *
 * <p>Loads only the web layer for {@link AiController} plus the {@link GlobalExceptionHandler},
 * and mocks {@link RagService} via {@link MockitoBean}. Verifies the grounded 200 response, the
 * validation error envelope for a blank question, and the {@code LLM_ERROR} envelope (HTTP 500)
 * when the underlying language model fails.
 */
@WebMvcTest(AiController.class)
@Import(GlobalExceptionHandler.class)
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RagService ragService;

    // The application enables JPA auditing (@EnableJpaAuditing); in a @WebMvcTest slice no JPA
    // entities are scanned, so the auditing infrastructure's mapping context bean fails to
    // build ("JPA metamodel must not be empty"). Mocking it lets the web slice context load.
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void ask_withValidQuestion_returns200() throws Exception {
        // Arrange
        AskRequest request = new AskRequest("What caused previous payment failures?");
        AskResponse response = new AskResponse(
                "Based on ticket TKT-1001, payment failures were caused by gateway timeouts.",
                List.of("TKT-1001"),
                true);
        when(ragService.ask(any(AskRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.sources[0]").value("TKT-1001"))
                .andExpect(jsonPath("$.answer").value(
                        "Based on ticket TKT-1001, payment failures were caused by gateway timeouts."));
    }

    @Test
    void ask_withBlankQuestion_returns400() throws Exception {
        // Arrange
        AskRequest request = new AskRequest("   ");

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void ask_whenLlmFails_returns500() throws Exception {
        // Arrange
        AskRequest request = new AskRequest("What caused previous payment failures?");
        when(ragService.ask(any(AskRequest.class)))
                .thenThrow(new LlmException("fail", new RuntimeException()));

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("LLM_ERROR"));
    }
}
