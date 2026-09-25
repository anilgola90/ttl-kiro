package com.example.kirotest.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.example.kirotest.domain.Comment;
import com.example.kirotest.domain.Priority;
import com.example.kirotest.domain.Ticket;
import com.example.kirotest.domain.TicketStatus;
import com.example.kirotest.dto.request.AddCommentRequest;
import com.example.kirotest.dto.response.CommentResponse;
import com.example.kirotest.event.CommentAddedEvent;
import com.example.kirotest.exception.TicketNotFoundException;
import com.example.kirotest.repository.CommentRepository;
import com.example.kirotest.repository.TicketRepository;

/**
 * Unit tests for {@link CommentServiceImpl}.
 *
 * <p>Collaborators are mocked so the tests exercise the service's orchestration in isolation:
 * loading the parent ticket, persisting the comment, touching the ticket's audit timestamp, and
 * publishing a {@link CommentAddedEvent}. Verifies requirements 6.1–6.5.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommentServiceImpl")
class CommentServiceImplTest {

    private static final String TICKET_ID = "TKT-1001";

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CommentServiceImpl commentService;

    @Captor
    private ArgumentCaptor<Comment> commentCaptor;

    @Captor
    private ArgumentCaptor<CommentAddedEvent> eventCaptor;

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static Ticket ticketFixture() {
        return Ticket.builder()
                .id(TICKET_ID)
                .title("Payment failure on checkout")
                .description("Customer reports card declined at checkout.")
                .status(TicketStatus.OPEN)
                .priority(Priority.HIGH)
                .assignee("jane.doe")
                .createdAt(Instant.parse("2025-01-15T10:30:00Z"))
                .updatedAt(Instant.parse("2025-01-15T10:30:00Z"))
                .build();
    }

    private static Comment savedCommentFixture(Ticket ticket) {
        return Comment.builder()
                .id(UUID.fromString("3f2504e0-4f89-41d3-9a0c-0305e82c3301"))
                .ticket(ticket)
                .body("Confirmed the fix in staging.")
                .author("jane.doe")
                .createdAt(Instant.parse("2025-01-15T11:00:00Z"))
                .build();
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("addComment with existing ticket persists the comment and publishes an event")
    void addComment_withExistingTicket_persistsAndPublishesEvent() {
        // Arrange
        Ticket ticket = ticketFixture();
        Comment saved = savedCommentFixture(ticket);
        AddCommentRequest request = new AddCommentRequest("Confirmed the fix in staging.", "jane.doe");

        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(commentRepository.save(any(Comment.class))).thenReturn(saved);

        // Act
        CommentResponse response = commentService.addComment(TICKET_ID, request);

        // Assert — response fields mirror the persisted comment
        assertThat(response.id()).isEqualTo(saved.getId());
        assertThat(response.body()).isEqualTo("Confirmed the fix in staging.");
        assertThat(response.author()).isEqualTo("jane.doe");
        assertThat(response.createdAt()).isEqualTo(saved.getCreatedAt());

        // Assert — the comment handed to the repository carries the request content and ticket link
        verify(commentRepository).save(commentCaptor.capture());
        Comment persisted = commentCaptor.getValue();
        assertThat(persisted.getBody()).isEqualTo("Confirmed the fix in staging.");
        assertThat(persisted.getAuthor()).isEqualTo("jane.doe");
        assertThat(persisted.getTicket()).isSameAs(ticket);

        // Assert — the parent ticket is re-saved to refresh updatedAt
        verify(ticketRepository).save(ticket);

        // Assert — a CommentAddedEvent is published with the saved comment and parent ticket
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        CommentAddedEvent event = eventCaptor.getValue();
        assertThat(event.comment()).isSameAs(saved);
        assertThat(event.ticket()).isSameAs(ticket);
    }

    @Test
    @DisplayName("addComment with non-existent ticket throws and never persists a comment")
    void addComment_withNonExistentTicket_throwsTicketNotFoundException() {
        // Arrange
        AddCommentRequest request = new AddCommentRequest("Confirmed the fix in staging.", "jane.doe");
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> commentService.addComment(TICKET_ID, request))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessageContaining(TICKET_ID);

        // Assert — no persistence or event side effects occur when the ticket is missing
        verify(commentRepository, never()).save(any(Comment.class));
        verify(ticketRepository, never()).save(any(Ticket.class));
        verify(eventPublisher, never()).publishEvent(any());
    }
}
