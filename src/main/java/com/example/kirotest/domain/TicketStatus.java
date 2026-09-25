package com.example.kirotest.domain;

/**
 * Lifecycle states a support ticket can occupy.
 *
 * <p>Persisted as {@code VARCHAR} via {@code @Enumerated(EnumType.STRING)} so that the
 * database stores the human-readable name rather than an ordinal, keeping the schema
 * resilient to future re-ordering of the enum constants.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    CANCELLED
}
