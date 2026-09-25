package com.example.kirotest.domain;

/**
 * Business priority assigned to a support ticket.
 *
 * <p>Persisted as {@code VARCHAR} via {@code @Enumerated(EnumType.STRING)} so the stored
 * value remains stable regardless of enum ordering.
 */
public enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
