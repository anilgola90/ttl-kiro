package com.example.kirotest.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.example.kirotest.domain.Comment;
import com.example.kirotest.domain.Priority;
import com.example.kirotest.domain.Ticket;
import com.example.kirotest.domain.TicketStatus;
import com.example.kirotest.dto.request.CreateTicketRequest;
import com.example.kirotest.dto.request.UpdateStatusRequest;
import com.example.kirotest.dto.request.UpdateTicketRequest;
import com.example.kirotest.dto.response.TicketResponse;
import com.example.kirotest.event.TicketCreatedEvent;
import com.example.kirotest.event.TicketUpdatedEvent;
import com.example.kirotest.exception.InvalidTransitionException;
import com.example.kirotest.exception.TicketNotFoundException;
import com.example.kirotest.repository.TicketRepository;

/**
 * Unit tests for {@link TicketServiceImpl}.
 *
 * <p>Pure Mockito unit tests — no Spring context. The {@link TicketStateMachine} is a plain,
 * dependency-free collaborator instantiated internally by the service, so it is intentionally
 * NOT mocked; the service is constructed manually with its two real collaborators mocked.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TicketServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TicketServiceImpl(ticketRepository, eventPublisher);
    }

    // ------------------------------------------------------------------
    // createTicket
    // ------------------------------------------------------------------

    @Test
    void createTicket_withValidInput_generatesTktIdAndPublishesEvent() {
        // Arrange
        when(ticketRepository.nextIdValue()).thenReturn(1001L);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateTicketRequest request = new CreateTicketRequest(
                "Payment gateway timeout",
                "Customers report timeouts after 10s.",
                Priority.HIGH,
                "jane.doe");

        // Act
        TicketResponse response = service.createTicket(request);

        // Assert
        ArgumentCaptor<Ticket> savedCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(savedCaptor.capture());
        Ticket saved = savedCaptor.getValue();
        assertThat(saved.getId()).isEqualTo("TKT-1001");
        assertThat(saved.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(saved.getTitle()).isEqualTo("Payment gateway timeout");
        assertThat(saved.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(saved.getAssignee()).isEqualTo("jane.doe");

        assertThat(response.id()).isEqualTo("TKT-1001");
        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);

        ArgumentCaptor<TicketCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(TicketCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().ticket().getId()).isEqualTo("TKT-1001");
    }

    // ------------------------------------------------------------------
    // getTicket
    // ------------------------------------------------------------------

    @Test
    void getTicket_withUnknownId_throwsTicketNotFoundException() {
        // Arrange
        when(ticketRepository.findById("TKT-9999")).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> service.getTicket("TKT-9999"))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void getTicket_withExistingId_returnsMappedResponse() {
        // Arrange
        Ticket ticket = ticket("TKT-1001", "Login broken", "Cannot log in",
                TicketStatus.OPEN, Priority.MEDIUM);
        ticket.getComments().add(comment(ticket, "Investigating", "jane.doe"));
        when(ticketRepository.findById("TKT-1001")).thenReturn(Optional.of(ticket));

        // Act
        TicketResponse response = service.getTicket("TKT-1001");

        // Assert
        assertThat(response.id()).isEqualTo("TKT-1001");
        assertThat(response.title()).isEqualTo("Login broken");
        assertThat(response.description()).isEqualTo("Cannot log in");
        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(response.comments()).hasSize(1);
        assertThat(response.comments().get(0).body()).isEqualTo("Investigating");
        assertThat(response.comments().get(0).author()).isEqualTo("jane.doe");
    }

    // ------------------------------------------------------------------
    // updateStatus
    // ------------------------------------------------------------------

    @Test
    void updateStatus_withValidTransition_persistsNewStatusAndPublishesEvent() {
        // Arrange
        Ticket ticket = ticket("TKT-1001", "Login broken", "Cannot log in",
                TicketStatus.OPEN, Priority.MEDIUM);
        when(ticketRepository.findById("TKT-1001")).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        UpdateStatusRequest request = new UpdateStatusRequest(TicketStatus.IN_PROGRESS, null);

        // Act
        TicketResponse response = service.updateStatus("TKT-1001", request);

        // Assert
        ArgumentCaptor<Ticket> savedCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(response.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        verify(eventPublisher).publishEvent(any(TicketUpdatedEvent.class));
    }

    @Test
    void updateStatus_withInvalidTransition_throwsInvalidTransitionException() {
        // Arrange
        Ticket ticket = ticket("TKT-1001", "Login broken", "Cannot log in",
                TicketStatus.CLOSED, Priority.MEDIUM);
        when(ticketRepository.findById("TKT-1001")).thenReturn(Optional.of(ticket));
        UpdateStatusRequest request = new UpdateStatusRequest(TicketStatus.OPEN, null);

        // Act / Assert
        assertThatThrownBy(() -> service.updateStatus("TKT-1001", request))
                .isInstanceOf(InvalidTransitionException.class);
        verify(ticketRepository, never()).save(any(Ticket.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ------------------------------------------------------------------
    // updateTicket
    // ------------------------------------------------------------------

    @Test
    void updateTicket_withPartialFields_updatesOnlyProvided() {
        // Arrange
        Ticket ticket = ticket("TKT-1001", "Old title", "Original description",
                TicketStatus.OPEN, Priority.LOW);
        when(ticketRepository.findById("TKT-1001")).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        UpdateTicketRequest request = new UpdateTicketRequest("New title", null, null, null);

        // Act
        TicketResponse response = service.updateTicket("TKT-1001", request);

        // Assert
        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.description()).isEqualTo("Original description");
        assertThat(response.priority()).isEqualTo(Priority.LOW);
        verify(eventPublisher).publishEvent(any(TicketUpdatedEvent.class));
    }

    // ------------------------------------------------------------------
    // listTickets — routing to the correct repository method
    // ------------------------------------------------------------------

    @Test
    void listTickets_withNoFilters_callsFindAll() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        when(ticketRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        // Act
        service.listTickets(null, null, pageable);

        // Assert
        verify(ticketRepository, times(1)).findAll(pageable);
    }

    @Test
    void listTickets_withStatusOnly_callsFindByStatus() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        when(ticketRepository.findByStatus(TicketStatus.OPEN, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        // Act
        service.listTickets(TicketStatus.OPEN, null, pageable);

        // Assert
        verify(ticketRepository, times(1)).findByStatus(TicketStatus.OPEN, pageable);
    }

    @Test
    void listTickets_withSearchOnly_callsSearchByKeyword() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        when(ticketRepository.searchByKeyword("payment", pageable))
                .thenReturn(new PageImpl<>(List.of()));

        // Act
        service.listTickets(null, "payment", pageable);

        // Assert
        verify(ticketRepository, times(1)).searchByKeyword("payment", pageable);
    }

    @Test
    void listTickets_withBoth_callsSearchByKeywordAndStatus() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        when(ticketRepository.searchByKeywordAndStatus("payment", TicketStatus.OPEN, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        // Act
        service.listTickets(TicketStatus.OPEN, "payment", pageable);

        // Assert
        verify(ticketRepository, times(1))
                .searchByKeywordAndStatus(eq("payment"), eq(TicketStatus.OPEN), eq(pageable));
    }

    // ------------------------------------------------------------------
    // Test fixtures
    // ------------------------------------------------------------------

    private static Ticket ticket(String id, String title, String description,
                                 TicketStatus status, Priority priority) {
        return Ticket.builder()
                .id(id)
                .title(title)
                .description(description)
                .status(status)
                .priority(priority)
                .comments(new ArrayList<>())
                .createdAt(Instant.parse("2025-01-15T10:30:00Z"))
                .updatedAt(Instant.parse("2025-01-15T10:30:00Z"))
                .build();
    }

    private static Comment comment(Ticket ticket, String body, String author) {
        return Comment.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .body(body)
                .author(author)
                .createdAt(Instant.parse("2025-01-15T11:00:00Z"))
                .build();
    }
}
