package com.example.kirotest.service;

import java.util.Map;
import java.util.Set;

import com.example.kirotest.domain.TicketStatus;
import com.example.kirotest.exception.InvalidTransitionException;

/**
 * Enforces the support ticket lifecycle by acting as the single authority on which status
 * transitions are permitted.
 *
 * <h2>Design decision — adjacency map as the single source of truth</h2>
 * <p>Valid transitions are modelled as an <em>adjacency map</em>
 * ({@code Map<TicketStatus, Set<TicketStatus>>}) where each key is a current status and the
 * associated value is the set of statuses reachable from it. Encoding the lifecycle as data
 * rather than as a tangle of {@code if}/{@code switch} branches means the transition rules live
 * in exactly one place: to change the lifecycle, you edit the map, not scattered conditionals.
 * This keeps the rules auditable at a glance and eliminates the risk of two code paths disagreeing
 * about what is legal.
 *
 * <h2>Design decision — plain Java, no Spring</h2>
 * <p>This class is intentionally <strong>not</strong> a Spring bean (no {@code @Component} or
 * {@code @Service}). The transition logic is pure and stateless, so it can be instantiated
 * directly by {@code TicketServiceImpl} and exercised in unit tests without the cost of bootstrapping
 * a Spring application context. Decoupling the business rule from the framework keeps it fast to
 * test (100% transition coverage is cheap) and reusable in any context.
 *
 * <p>The map is declared {@code static} and immutable ({@link Map#of} / {@link Set#of}) because the
 * lifecycle rules are constant for the lifetime of the application and safe to share across threads.
 */
public final class TicketStateMachine {

    /**
     * Adjacency map describing the allowed lifecycle transitions.
     *
     * <ul>
     *   <li>{@code OPEN}        &rarr; {@code IN_PROGRESS}, {@code CANCELLED}</li>
     *   <li>{@code IN_PROGRESS} &rarr; {@code RESOLVED}, {@code CANCELLED}</li>
     *   <li>{@code RESOLVED}    &rarr; {@code CLOSED}</li>
     *   <li>{@code CLOSED}      &rarr; (terminal — no transitions)</li>
     *   <li>{@code CANCELLED}   &rarr; (terminal — no transitions)</li>
     * </ul>
     */
    private static final Map<TicketStatus, Set<TicketStatus>> VALID_TRANSITIONS = Map.of(
            TicketStatus.OPEN, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED),
            TicketStatus.IN_PROGRESS, Set.of(TicketStatus.RESOLVED, TicketStatus.CANCELLED),
            TicketStatus.RESOLVED, Set.of(TicketStatus.CLOSED),
            TicketStatus.CLOSED, Set.of(),
            TicketStatus.CANCELLED, Set.of()
    );

    /**
     * Validates a requested status transition and returns the target status when it is permitted.
     *
     * @param current the status the ticket is currently in
     * @param target  the status the caller wishes to move the ticket to
     * @return {@code target} when the transition is allowed by the lifecycle rules
     * @throws InvalidTransitionException if {@code target} is not reachable from {@code current}
     */
    public TicketStatus transition(TicketStatus current, TicketStatus target) {
        if (!VALID_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new InvalidTransitionException(current, target);
        }
        return target;
    }

    /**
     * Non-throwing check for whether a transition is permitted, useful for callers (such as UI
     * enablement logic) that want to test legality without triggering an exception.
     *
     * @param current the status the ticket is currently in
     * @param target  the status the caller wishes to move the ticket to
     * @return {@code true} if {@code target} is reachable from {@code current}, otherwise {@code false}
     */
    public boolean isValidTransition(TicketStatus current, TicketStatus target) {
        return VALID_TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
    }
}
