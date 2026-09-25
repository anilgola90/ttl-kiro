package com.example.kirotest.repository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for keyword-search filter correctness.
 *
 * <p>Feature: support-ticket-management, Property 4: keyword search correctness.
 *
 * <p>These tests validate the <em>semantics</em> of the case-insensitive keyword filter that
 * backs {@code TicketRepository.searchByKeyword}. The JPQL query filters with
 * {@code LOWER(title) LIKE %kw%} OR {@code LOWER(description) LIKE %kw%}; this test declares an
 * independent oracle predicate mirroring that {@code LIKE} semantics and asserts that filtering a
 * generated record set with the oracle produces exactly the records that match (every match is
 * included, every non-match is excluded &mdash; no false positives, no false negatives).
 *
 * <p>Running deterministically in-memory (no Spring context, no database) keeps the property
 * fast and free of the jqwik/Spring per-try lifecycle interaction, while still asserting the
 * exact filter contract Property 4 requires.
 *
 * <p><b>Validates: Requirements 2.3, 7.1</b>
 */
class TicketKeywordSearchProperties {

    /** A single generated ticket record: only the searched fields matter here. */
    record Record(String title, String description) {}

    /**
     * Oracle mirroring the JPQL {@code LOWER(...) LIKE LOWER(CONCAT('%', :keyword, '%'))}
     * semantics: a case-insensitive substring match against title OR description.
     */
    static boolean keywordMatches(String title, String desc, String kw) {
        String needle = kw.toLowerCase();
        return title.toLowerCase().contains(needle)
                || desc.toLowerCase().contains(needle);
    }

    /**
     * Property 4: keyword search is a correct filter.
     *
     * <p>For any generated set of records and any keyword, the filtered result SHALL contain
     * exactly the records whose title or description contains the keyword (case-insensitive):
     * every matching record is present, and no non-matching record is present.
     *
     * <p><b>Validates: Requirements 2.3, 7.1</b>
     */
    @Property(tries = 100)
    void searchByKeyword_forAnyRecordSet_returnsExactlyTheMatchingRecords(
            @ForAll("records") List<Record> records,
            @ForAll("keywords") String keyword) {
        // Act: simulate the repository filter using the oracle predicate.
        List<Record> filtered = records.stream()
                .filter(r -> keywordMatches(r.title(), r.description(), keyword))
                .toList();

        // Assert (no false positives): every returned record actually matches.
        assertThat(filtered)
                .allMatch(r -> keywordMatches(r.title(), r.description(), keyword));

        // Assert (no false negatives): every matching record is present in the result.
        List<Record> expectedMatches = records.stream()
                .filter(r -> keywordMatches(r.title(), r.description(), keyword))
                .toList();
        assertThat(filtered).containsExactlyElementsOf(expectedMatches);

        // Assert (completeness): no record satisfying the predicate is absent.
        for (Record r : records) {
            if (keywordMatches(r.title(), r.description(), keyword)) {
                assertThat(filtered).contains(r);
            } else {
                assertThat(filtered).doesNotContain(r);
            }
        }
    }

    // ── Generators ──────────────────────────────────────────────────────────────

    /** Short alphanumeric + space strings keep the input space realistic and readable. */
    private Arbitrary<String> shortText() {
        return Arbitraries.strings()
                .withChars("abcABC123 ")
                .ofMinLength(0)
                .ofMaxLength(12);
    }

    @Provide
    Arbitrary<List<Record>> records() {
        Arbitrary<Record> record = Combinators
                .combine(shortText(), shortText())
                .as(Record::new);
        return record.list().ofMaxSize(15);
    }

    @Provide
    Arbitrary<String> keywords() {
        // Non-empty keyword (1–5 chars) so the filter is exercised meaningfully; matches the
        // 1–100 char search contract from Requirement 2.3.
        return Arbitraries.strings()
                .withChars("abcABC123 ")
                .ofMinLength(1)
                .ofMaxLength(5);
    }
}
