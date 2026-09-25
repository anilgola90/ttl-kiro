package com.example.kirotest.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.kirotest.domain.Ticket;
import com.example.kirotest.domain.TicketStatus;

/**
 * Spring Data JPA repository for {@link Ticket} aggregate roots.
 *
 * <p>The primary key type is {@link String} because tickets use a human-readable business
 * identifier (e.g. {@code "TKT-1001"}) rather than a surrogate numeric key.
 *
 * <p>Keyword search is expressed in JPQL rather than native SQL so the queries remain
 * database-agnostic (allowing an in-memory/H2 database in tests) while still supporting
 * case-insensitive matching on the title and description fields. The one native query,
 * {@link #nextIdValue()}, is required to read from the dedicated PostgreSQL sequence used
 * for ticket ID generation.
 */
public interface TicketRepository extends JpaRepository<Ticket, String> {

    /**
     * Returns a page of tickets restricted to a single lifecycle status.
     *
     * <p>Backs the list endpoint when only a status filter is supplied.
     *
     * @param status   the status to filter by
     * @param pageable pagination and sort information
     * @return a page of tickets matching the given status
     */
    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    /**
     * Performs a case-insensitive keyword search across the ticket title and description.
     *
     * <p>A ticket matches when the keyword appears (case-insensitively) in either its
     * {@code title} or its {@code description}. Kept as JPQL rather than native SQL to remain
     * portable across databases.
     *
     * @param keyword  the substring to search for in title or description
     * @param pageable pagination and sort information
     * @return a page of tickets whose title or description contains the keyword
     */
    @Query("""
        SELECT t FROM Ticket t
        WHERE LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
        """)
    Page<Ticket> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Performs a conjunctive status + keyword search.
     *
     * <p>A ticket matches only when it has the given status <em>and</em> the keyword appears
     * (case-insensitively) in either its {@code title} or {@code description}. Backs the list
     * endpoint when both a status filter and a search term are supplied.
     *
     * @param keyword  the substring to search for in title or description
     * @param status   the status the ticket must also have
     * @param pageable pagination and sort information
     * @return a page of tickets matching both the status and the keyword
     */
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = :status
          AND (LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR  LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    Page<Ticket> searchByKeywordAndStatus(
        @Param("keyword") String keyword,
        @Param("status") TicketStatus status,
        Pageable pageable);

    /**
     * Fetches the next value from the {@code ticket_id_seq} PostgreSQL sequence.
     *
     * <p>Used by the service layer to construct the human-readable ticket identifier
     * ({@code "TKT-" + nextIdValue()}). This is a native query because reading a sequence
     * value is a database-specific operation with no portable JPQL equivalent.
     *
     * @return the next numeric value in the ticket ID sequence
     */
    @Query(value = "SELECT nextval('ticket_id_seq')", nativeQuery = true)
    Long nextIdValue();
}
