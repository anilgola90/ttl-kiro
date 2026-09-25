package com.example.kirotest.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.stubbing.Answer;
import org.springframework.context.ApplicationEventPublisher;

import com.example.kirotest.domain.Priority;
import com.example.kirotest.domain.Ticket;
import com.example.kirotest.domain.TicketStatus;
import com.example.kirotest.dto.request.UpdateTicketRequest;
import com.example.kirotest.dto.response.TicketResponse;
import com.example.kirotest.repository.TicketRepository;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;

/**
 * Property-based test for partial (PATCH) ticket updates.
 *
 * <p>Feature: support-ticket-management, Property 10: partial update preserves unmodified fields.
 *
 * <p>This is a pure unit-level property test — {@link TicketServiceImpl} is exercised directly
 * with a mocked {@link TicketRepository} and {@link ApplicationEventPublisher}, so no Spring
 * context is started. {@code findById} returns the generated original ticket and {@code save}
 * echoes back its argument, letting us observe exactly what the service mutated.
 *
 * <p>For any existing ticket and any independently-chosen subset of the updatable fields
 * {@code {title, description, priority, assignee}} present in the {@link UpdateTicketRequest}:
 * <ul>
 *   <li>every field supplied (non-null) in the payload SHALL equal the payload value, and</li>
 *   <li>every field absent (null) from the payload SHALL retain its original value.</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 4.1, 4.5</b>
 */
class PartialUpdateProperties {

    private static final String TICKET_ID = "TKT-1001";

    /**
     * Property 10: partial update preserves unmodified fields.
     *
     * <p>Each of the four updatable fields is independently either left unchanged (payload value
     * {@code null}) or set to a freshly generated value, driven by the {@code set*} booleans.
     * After {@link TicketServiceImpl#updateTicket} the returned {@link TicketResponse} is asserted
     * field-by-field against the intended outcome.
     *
     * <p>Generated "new" strings are constrained to be non-blank (length &ge; 1) because
     * {@code UpdateTicketRequest} carries {@code @Size(min = 1)} on title/description — a blank
     * value is not a valid "update" payload.
     *
     * <p><b>Validates: Requirements 4.1, 4.5</b>
     */
    @Property(tries = 100)
    void updateTicket_forAnyFieldSubset_appliesSuppliedFieldsAndPreservesTheRest(
            // Original ticket field values.
            @ForAll @StringLength(min = 1, max = 200) String origTitle,
            @ForAll @StringLength(min = 1, max = 5000) String origDescription,
            @ForAll Priority origPriority,
            @ForAll @Size(max = 100) String origAssignee,
            // Which fields the PATCH payload sets.
            @ForAll boolean setTitle,
            @ForAll boolean setDescription,
            @ForAll boolean setPriority,
            @ForAll boolean setAssignee,
            // Candidate new values (only used when the corresponding set* flag is true).
            @ForAll @StringLength(min = 1, max = 200) String newTitle,
            @ForAll @StringLength(min = 1, max = 5000) String newDescription,
            @ForAll Priority newPriority,
            @ForAll @Size(max = 100) String newAssignee) {

        // Arrange: build the original ticket in a stable, known state.
        Ticket original = Ticket.builder()
                .id(TICKET_ID)
                .title(origTitle)
                .description(origDescription)
                .status(TicketStatus.OPEN)
                .priority(origPriority)
                .assignee(origAssignee)
                .resolutionNotes(null)
                .comments(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        TicketRepository ticketRepository = mock(TicketRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(ticketRepository.findById(anyString())).thenReturn(Optional.of(original));
        // save echoes its argument so we observe exactly what the service mutated.
        when(ticketRepository.save(any(Ticket.class)))
                .thenAnswer((Answer<Ticket>) invocation -> invocation.getArgument(0));

        TicketServiceImpl service = new TicketServiceImpl(ticketRepository, eventPublisher);

        // Build the PATCH request: each field is either null (leave unchanged) or a new value.
        UpdateTicketRequest request = new UpdateTicketRequest(
                setTitle ? newTitle : null,
                setDescription ? newDescription : null,
                setPriority ? newPriority : null,
                setAssignee ? newAssignee : null);

        // The expected outcome per field: the supplied value if set, else the original.
        String expectedTitle = setTitle ? newTitle : origTitle;
        String expectedDescription = setDescription ? newDescription : origDescription;
        Priority expectedPriority = setPriority ? newPriority : origPriority;
        String expectedAssignee = setAssignee ? newAssignee : origAssignee;

        // Act
        TicketResponse response = service.updateTicket(TICKET_ID, request);

        // Assert: supplied fields equal the payload; absent fields retain the original value.
        assertThat(response.title()).isEqualTo(expectedTitle);
        assertThat(response.description()).isEqualTo(expectedDescription);
        assertThat(response.priority()).isEqualTo(expectedPriority);
        assertThat(response.assignee()).isEqualTo(expectedAssignee);

        // Sanity: fields never targeted by PATCH semantics are untouched.
        assertThat(response.id()).isEqualTo(TICKET_ID);
        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
    }
}
