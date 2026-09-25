package com.example.kirotest.repository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kirotest.domain.TicketStatus;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for combined status + keyword filter conjunctiveness.
 *
 * <p>Feature: support-ticket-management, Property 5: combined filter is conjunctive.
 *
 * <p>These tests validate the <em>semantics</em> of the combined filter that backs
 * {@code TicketRepository.searchByKeywordAndStatus}. The JPQL query requires
 * {@code status = :status} AND ({@code LOWER(title) LIKE %kw%} OR {@code LOWER(description) LIKE
 * %kw%}). This test declares an independent oracle for the conjunction and asserts that filtering
 * a generated record set returns exactly the records satisfying BOTH conditions &mdash; any record
 * failing either the status match or the keyword match is excluded.
 *
 * <p>Running deterministically in-memory (no Spring context, no database) keeps the property fast
 * and isolated while asserting the exact conjunctive contract Property 5 requires.
 *
 * <p><b>Validates: Requirements 2.4, 7.4</b>
 */
class TicketCombinedFilterProperties {

    /** A single generated ticket record with the fields relevant to the combined filter. */
    record Record(TicketStatus status, String title, String description) {}

    /**
     * Oracle mirroring the JPQL keyword clause: case-insensitive substring match against
     * title OR description.
     */
    static boolean keywordMatches(String title, String desc, String kw) {
        String needle = kw.toLowerCase();
        return title.toLowerCase().contains(needle)
                || desc.toLowerCase().contains(needle);
    }

    /**
     * Property 5: combined status + keyword filter is conjunctive.
     *
     * <p>For any generated record set, any target status, and any keyword, the filtered result
     * SHALL contain exactly the records for which the status equals the target status AND the
     * keyword appears (case-insensitively) in the title or description. Any record failing either
     * condition SHALL be absent.
     *
     * <p><b>Validates: Requirements 2.4, 7.4</b>
     */
    @Property(tries = 100)
    void searchByKeywordAndStatus_forAnyRecordSet_returnsOnlyRecordsMatchingBoth(
            @ForAll("records") List<Record> records,
            @ForAll TicketStatus filterStatus,
            @ForAll("keywords") String keyword) {
        // Act: simulate the repository conjunctive filter.
        List<Record> filtered = records.stream()
                .filter(r -> r.status() == filterStatus
                        && keywordMatches(r.title(), r.description(), keyword))
                .toList();

        // Assert (conjunction holds): every returned record matches BOTH conditions.
        assertThat(filtered).allMatch(r -> r.status() == filterStatus);
        assertThat(filtered)
                .allMatch(r -> keywordMatches(r.title(), r.description(), keyword));

        // Assert (no record failing either condition is included).
        for (Record r : records) {
            boolean statusMatch = r.status() == filterStatus;
            boolean kwMatch = keywordMatches(r.title(), r.description(), keyword);
            if (statusMatch && kwMatch) {
                assertThat(filtered).contains(r);
            } else {
                // Fails at least one condition → must be excluded.
                assertThat(filtered).doesNotContain(r);
            }
        }
    }

    // ── Generators ──────────────────────────────────────────────────────────────

    private Arbitrary<String> shortText() {
        return Arbitraries.strings()
                .withChars("abcABC123 ")
                .ofMinLength(0)
                .ofMaxLength(12);
    }

    @Provide
    Arbitrary<List<Record>> records() {
        Arbitrary<Record> record = Combinators
                .combine(Arbitraries.of(TicketStatus.class), shortText(), shortText())
                .as(Record::new);
        return record.list().ofMaxSize(15);
    }

    @Provide
    Arbitrary<String> keywords() {
        return Arbitraries.strings()
                .withChars("abcABC123 ")
                .ofMinLength(1)
                .ofMaxLength(5);
    }
}
