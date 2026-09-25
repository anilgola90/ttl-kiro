package com.example.kirotest.controller;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.kirotest.dto.request.AddCommentRequest;
import com.example.kirotest.dto.response.CommentResponse;
import com.example.kirotest.exception.GlobalExceptionHandler;
import com.example.kirotest.exception.TicketNotFoundException;
import com.example.kirotest.service.CommentService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code @WebMvcTest} slice tests for {@link CommentController}.
 *
 * <p>Loads only the web layer for {@link CommentController} plus the
 * {@link GlobalExceptionHandler}, and mocks {@link CommentService} via {@link MockitoBean}.
 * Verifies the 201 + {@code Location} header on success, the validation error envelope on a
 * blank body, and the not-found envelope when the owning ticket does not exist.
 */
@WebMvcTest(CommentController.class)
@Import(GlobalExceptionHandler.class)
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommentService commentService;

    // The application enables JPA auditing (@EnableJpaAuditing); in a @WebMvcTest slice no JPA
    // entities are scanned, so the auditing infrastructure's mapping context bean fails to
    // build ("JPA metamodel must not be empty"). Mocking it lets the web slice context load.
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void addComment_withValidBody_returns201() throws Exception {
        // Arrange
        UUID commentId = UUID.fromString("3f2504e0-4f89-41d3-9a0c-0305e82c3301");
        AddCommentRequest request = new AddCommentRequest("Confirmed the fix in staging.", "jane.doe");
        CommentResponse response = new CommentResponse(
                commentId,
                "Confirmed the fix in staging.",
                "jane.doe",
                Instant.parse("2025-01-15T10:30:00Z"));
        when(commentService.addComment(eq("TKT-1001"), any(AddCommentRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/tickets/{id}/comments", "TKT-1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/tickets/TKT-1001/comments/" + commentId))
                .andExpect(jsonPath("$.id").value(commentId.toString()))
                .andExpect(jsonPath("$.author").value("jane.doe"));
    }

    @Test
    void addComment_withBlankBody_returns400() throws Exception {
        // Arrange
        AddCommentRequest request = new AddCommentRequest("   ", "jane.doe");

        // Act & Assert
        mockMvc.perform(post("/api/v1/tickets/{id}/comments", "TKT-1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void addComment_whenTicketNotFound_returns404() throws Exception {
        // Arrange
        AddCommentRequest request = new AddCommentRequest("Confirmed the fix in staging.", "jane.doe");
        when(commentService.addComment(eq("TKT-9999"), any(AddCommentRequest.class)))
                .thenThrow(new TicketNotFoundException("TKT-9999"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/tickets/{id}/comments", "TKT-9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("TICKET_NOT_FOUND"));
    }
}
