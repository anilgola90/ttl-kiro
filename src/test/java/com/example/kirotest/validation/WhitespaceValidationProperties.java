package com.example.kirotest.validation;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kirotest.domain.Priority;
import com.example.kirotest.dto.request.AddCommentRequest;
import com.example.kirotest.dto.request.AskRequest;
import com.example.kirotest.dto.request.CreateTicketRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for whitespace-only input rejection on request DTOs.
 *
 * <p>Feature: support-ticket-management, Property 3: whitespace inputs rejected.
 *
 * <p><b>Property 3 — Whitespace-only inputs are always rejected:</b> for any string composed
 * solely of whitespace characters (spaces, tabs, newlines) supplied to a required text field
 * (ticket title/description, ask question, comment body/author), Jakarta Bean Validation SHALL
 * report the DTO as invalid with a {@code @NotBlank} violation whose property path names the
 * offending field. Other required fields are populated with valid values so the only violation
 * under test is the whitespace field.
 *
 * <p>The tests exercise the Bean Validation API directly (no Spring context) for speed, mirroring
 * the {@code @Valid} enforcement that runs at the controller boundary.
 *
 * <p>Validates: Requirements 1.3, 1.4, 6.2, 10.6.
 */
class WhitespaceValidationProperties {

    private static final Validator VALIDATOR;

    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        VALIDATOR = factory.getValidator();
    }

    /**
     * A whitespace-only title on {@link CreateTicketRequest} always yields a violation on "title".
     *
     * <p>Validates: Requirements 1.3, 1.4.
     */
    @Property(tries = 100)
    void createTicket_whitespaceTitle_violatesTitle(@ForAll("whitespaceStrings") String title) {
        // Arrange — valid description + priority; only title is whitespace.
        CreateTicketRequest request = new CreateTicketRequest(title, "Valid description", Priority.HIGH, null);

        // Act
        Set<ConstraintViolation<CreateTicketRequest>> violations = VALIDATOR.validate(request);

        // Assert
        assertThat(violations).isNotEmpty();
        assertThat(violatedPaths(violations)).contains("title");
    }

    /**
     * A whitespace-only description on {@link CreateTicketRequest} always violates "description".
     *
     * <p>Validates: Requirements 1.3, 1.4.
     */
    @Property(tries = 100)
    void createTicket_whitespaceDescription_violatesDescription(
            @ForAll("whitespaceStrings") String description) {
        // Arrange — valid title + priority; only description is whitespace.
        CreateTicketRequest request = new CreateTicketRequest("Valid title", description, Priority.HIGH, null);

        // Act
        Set<ConstraintViolation<CreateTicketRequest>> violations = VALIDATOR.validate(request);

        // Assert
        assertThat(violations).isNotEmpty();
        assertThat(violatedPaths(violations)).contains("description");
    }

    /**
     * A whitespace-only question on {@link AskRequest} always violates "question".
     *
     * <p>Validates: Requirements 6.2.
     */
    @Property(tries = 100)
    void ask_whitespaceQuestion_violatesQuestion(@ForAll("whitespaceStrings") String question) {
        // Arrange
        AskRequest request = new AskRequest(question);

        // Act
        Set<ConstraintViolation<AskRequest>> violations = VALIDATOR.validate(request);

        // Assert
        assertThat(violations).isNotEmpty();
        assertThat(violatedPaths(violations)).contains("question");
    }

    /**
     * A whitespace-only body on {@link AddCommentRequest} always violates "body".
     *
     * <p>Validates: Requirements 10.6.
     */
    @Property(tries = 100)
    void addComment_whitespaceBody_violatesBody(@ForAll("whitespaceStrings") String body) {
        // Arrange — valid author; only body is whitespace.
        AddCommentRequest request = new AddCommentRequest(body, "jane.doe");

        // Act
        Set<ConstraintViolation<AddCommentRequest>> violations = VALIDATOR.validate(request);

        // Assert
        assertThat(violations).isNotEmpty();
        assertThat(violatedPaths(violations)).contains("body");
    }

    /**
     * A whitespace-only author on {@link AddCommentRequest} always violates "author".
     *
     * <p>Validates: Requirements 10.6.
     */
    @Property(tries = 100)
    void addComment_whitespaceAuthor_violatesAuthor(@ForAll("whitespaceStrings") String author) {
        // Arrange — valid body; only author is whitespace.
        AddCommentRequest request = new AddCommentRequest("Valid comment body", author);

        // Act
        Set<ConstraintViolation<AddCommentRequest>> violations = VALIDATOR.validate(request);

        // Assert
        assertThat(violations).isNotEmpty();
        assertThat(violatedPaths(violations)).contains("author");
    }

    /**
     * Collects the leaf property names from a set of constraint violations.
     */
    private static <T> List<String> violatedPaths(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .toList();
    }

    /**
     * Strings composed only of whitespace characters (space, tab, newline), length 1..20.
     * {@code @NotBlank} must treat every such string as blank and reject it.
     */
    @Provide
    Arbitrary<String> whitespaceStrings() {
        return Arbitraries.of(" ", "\t", "\n")
                .list()
                .ofMinSize(1)
                .ofMaxSize(20)
                .map(list -> String.join("", list));
    }
}
