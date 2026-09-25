package com.example.kirotest.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Aggregate root representing a support ticket and its full lifecycle.
 *
 * <p>The identifier is a human-readable business key of the form {@code "TKT-1001"} generated
 * from a dedicated PostgreSQL sequence, rather than a surrogate numeric key, so it can be
 * surfaced directly in the API and UI.
 *
 * <p>Auditing timestamps are populated automatically by Spring Data via
 * {@link AuditingEntityListener}; {@code createdAt} is never updated after insert.
 */
@Entity
@Table(name = "tickets")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    @Id
    @Column(name = "id", length = 20)
    private String id;                        // "TKT-1001"

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 5000, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;              // OPEN | IN_PROGRESS | RESOLVED | CLOSED | CANCELLED

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;                // LOW | MEDIUM | HIGH | CRITICAL

    @Column(length = 100)
    private String assignee;

    @Column(length = 2000)
    private String resolutionNotes;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Comment> comments = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
