package com.example.kirotest.service;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.kirotest.domain.TicketStatus;
import com.example.kirotest.exception.InvalidTransitionException;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/**
 * Property-based tests for {@link TicketStateMachine} transition correctness.
 *
 * <p>Feature: support-ticket-management, Property 1+2: state machine transition correctness.
 *
 * <p>These tests treat the state machine as a black box and verify its behaviour against an
 * independently-declared expectation of the lifecycle rules ({@link #EXPECTED_VALID}). Because
 * jqwik auto-generates all values of the {@link TicketStatus} enum, running with
 * {@code tries = 100} exhaustively covers all 5 &times; 5 = 25 {@code (from, to)} pairs many times
 * over.
 *
 * <ul>
 *   <li><b>Property 1</b> — the machine rejects <em>every</em> transition that is not in the
 *       expected valid-next set by throwing {@link InvalidTransitionException}.</li>
 *   <li><b>Property 2</b> — the machine accepts <em>every</em> transition that is in the expected
 *       valid-next set, returning the target status without throwing.</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 5.1&ndash;5.7</b>
 */
class TicketStateMachineProperties {

    /**
     * Independent mirror of the lifecycle rules the state machine is expected to enforce.
     * Declared separately from the production {@code VALID_TRANSITIONS} map so the tests fail if
     * the two ever diverge.
     */
    private static final Map<TicketStatus, Set<TicketStatus>> EXPECTED_VALID = Map.of(
            TicketStatus.OPEN, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED),
            TicketStatus.IN_PROGRESS, Set.of(TicketStatus.RESOLVED, TicketStatus.CANCELLED),
            TicketStatus.RESOLVED, Set.of(TicketStatus.CLOSED),
            TicketStatus.CLOSED, Set.of(),
            TicketStatus.CANCELLED, Set.of()
    );

    private static boolean isExpectedValid(TicketStatus from, TicketStatus to) {
        return EXPECTED_VALID.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Property 1: the state machine rejects all invalid transitions.
     *
     * <p>For any {@code (from, to)} pair where {@code to} is NOT in the expected valid-next set for
     * {@code from}, {@link TicketStateMachine#transition} must throw
     * {@link InvalidTransitionException}.
     *
     * <p><b>Validates: Requirements 5.1&ndash;5.7</b>
     */
    @Property(tries = 100)
    void transition_forAnyInvalidPair_throwsInvalidTransitionException(
            @ForAll TicketStatus from, @ForAll TicketStatus to) {
        // Arrange: focus this property on the invalid pairs only.
        Assume.that(!isExpectedValid(from, to));
        TicketStateMachine sm = new TicketStateMachine();

        // Act + Assert
        assertThatThrownBy(() -> sm.transition(from, to))
                .isInstanceOf(InvalidTransitionException.class);
    }

    /**
     * Property 2: the state machine accepts all valid transitions.
     *
     * <p>For any {@code (from, to)} pair where {@code to} IS in the expected valid-next set for
     * {@code from}, {@link TicketStateMachine#transition} must return {@code to} without throwing.
     *
     * <p><b>Validates: Requirements 5.1&ndash;5.7</b>
     */
    @Property(tries = 100)
    void transition_forAnyValidPair_returnsTargetStatus(
            @ForAll TicketStatus from, @ForAll TicketStatus to) {
        // Arrange: focus this property on the valid pairs only.
        Assume.that(isExpectedValid(from, to));
        TicketStateMachine sm = new TicketStateMachine();

        // Act + Assert
        assertThat(sm.transition(from, to)).isEqualTo(to);
    }
}
