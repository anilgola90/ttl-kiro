package com.example.kirotest.controller;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.kirotest.domain.Priority;
import com.example.kirotest.domain.TicketStatus;
import com.example.kirotest.dto.request.CreateTicketRequest;
import com.example.kirotest.dto.request.UpdateStatusRequest;
import com.example.kirotest.dto.response.PagedResponse;
import com.example.kirotest.dto.response.TicketResponse;
import com.example.kirotest.dto.response.TicketSummaryResponse;
import com.example.kirotest.exception.GlobalExceptionHandler;
import com.example.kirotest.exception.InvalidTransitionException;
import com.example.kirotest.exception.TicketNotFoundException;
import com.example.kirotest.service.TicketService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code @WebMvcTest} slice tests for {@link TicketController}.
 *
 * <p>Loads only the web layer for {@link TicketController} plus the
 * {@link GlobalExceptionHandler} so error envelopes are produced, and mocks
 * {@link TicketService} via {@link MockitoBean}. Verifies HTTP status codes, the
 * {@code Location} header on create, the paginated envelope on list, and the error
 * envelope produced for validation and domain failures.
 */
@WebMvcTest(TicketController.class)
@Import(GlobalExceptionHandler.class)
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TicketService ticketService;

    // The application enables JPA auditing (@EnableJpaAuditing); in a @WebMvcTest slice no JPA
    // entities are scanned, so the auditing infrastructure's mapping context bean fails to
    // build ("JPA metamodel must not be empty"). Mocking it lets the web slice context load.
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private TicketResponse sampleTicket(String id, TicketStatus status) {
        return new TicketResponse(
                id,
                "Payment gateway timeout",
                "Customers report timeouts when submitting card payments.",
                status,
                Priority.HIGH,
                "jane.doe",
                null,
                Instant.parse("2025-01-15T10:30:00Z"),
                Instant.parse("2025-01-15T12:45:00Z"),
                List.of());
    }

    @Test
    void createTicket_withValidBody_returns201() throws Exception {
        // Arrange
        CreateTicketRequest request = new CreateTicketRequest(
                "Payment gateway timeout",
                "Customers report timeouts when submitting card payments.",
                Priority.HIGH,
                "jane.doe");
        when(ticketService.createTicket(any(CreateTicketRequest.class)))
                .thenReturn(sampleTicket("TKT-1001", TicketStatus.OPEN));

        // Act & Assert
        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/tickets/TKT-1001"))
                .andExpect(jsonPath("$.id").value("TKT-1001"));
    }

    @Test
    void createTicket_withBlankTitle_returns400() throws Exception {
        // Arrange
        CreateTicketRequest request = new CreateTicketRequest(
                "   ",
                "A valid description with content.",
                Priority.HIGH,
                "jane.doe");

        // Act & Assert
        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createTicket_withMissingPriority_returns400() throws Exception {
        // Arrange: priority omitted from the JSON body
        String body = """
                {
                  "title": "Payment gateway timeout",
                  "description": "A valid description with content.",
                  "assignee": "jane.doe"
                }
                """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTicket_whenFound_returns200() throws Exception {
        // Arrange
        when(ticketService.getTicket("TKT-1001"))
                .thenReturn(sampleTicket("TKT-1001", TicketStatus.OPEN));

        // Act & Assert
        mockMvc.perform(get("/api/v1/tickets/{id}", "TKT-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("TKT-1001"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void getTicket_whenNotFound_returns404() throws Exception {
        // Arrange
        when(ticketService.getTicket("TKT-9999"))
                .thenThrow(new TicketNotFoundException("TKT-9999"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/tickets/{id}", "TKT-9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("TICKET_NOT_FOUND"));
    }

    @Test
    void listTickets_returns200WithPagedEnvelope() throws Exception {
        // Arrange
        TicketSummaryResponse summary = new TicketSummaryResponse(
                "TKT-1001",
                "Payment gateway timeout",
                TicketStatus.OPEN,
                Priority.HIGH,
                "jane.doe",
                Instant.parse("2025-01-15T10:30:00Z"),
                Instant.parse("2025-01-15T12:45:00Z"));
        PagedResponse<TicketSummaryResponse> paged =
                new PagedResponse<>(List.of(summary), 0, 20, 1L, 1);
        when(ticketService.listTickets(any(), any(), any(Pageable.class))).thenReturn(paged);

        // Act & Assert
        mockMvc.perform(get("/api/v1/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("TKT-1001"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void updateStatus_whenInvalidTransition_returns422() throws Exception {
        // Arrange
        UpdateStatusRequest request = new UpdateStatusRequest(TicketStatus.OPEN, null);
        when(ticketService.updateStatus(eq("TKT-1001"), any(UpdateStatusRequest.class)))
                .thenThrow(new InvalidTransitionException(TicketStatus.CLOSED, TicketStatus.OPEN));

        // Act & Assert
        mockMvc.perform(patch("/api/v1/tickets/{id}/status", "TKT-1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));

        // The controller delegated exactly the status transition and nothing else.
        Mockito.verify(ticketService).updateStatus(eq("TKT-1001"), any(UpdateStatusRequest.class));
        Mockito.verifyNoMoreInteractions(ticketService);
    }
}
