package com.example.kirotest.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.kirotest.dto.request.AddCommentRequest;
import com.example.kirotest.dto.response.CommentResponse;
import com.example.kirotest.service.CommentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller exposing the comment sub-resource of a support ticket.
 *
 * <p>Comments are always scoped to their owning ticket, hence the nested path
 * {@code /api/v1/tickets/{id}/comments}. This controller handles HTTP concerns only —
 * validation of the request body and translation of the service result into an HTTP
 * response — while all business logic lives in {@link CommentService}.
 */
@RestController
@RequestMapping("/api/v1/tickets/{id}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * Adds a comment to an existing ticket.
     *
     * <p>Delegates persistence and event publication to {@link CommentService#addComment}, then
     * returns HTTP 201 with a {@code Location} header pointing at the newly created comment so
     * clients can dereference it directly.
     *
     * @param id      the identifier of the ticket to comment on (path variable)
     * @param request the validated comment payload (body and author)
     * @return HTTP 201 Created carrying the persisted {@link CommentResponse} and a
     *         {@code Location} header for the new comment
     */
    @Operation(summary = "Add a comment to a ticket",
            description = "Persists a new comment against the given ticket and triggers asynchronous "
                    + "re-ingestion of the comment into the retrieval context.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comment created"),
            @ApiResponse(responseCode = "400", description = "Validation error (blank body or author)"),
            @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    @PostMapping
    public ResponseEntity<CommentResponse> add(@PathVariable String id,
                                               @Valid @RequestBody AddCommentRequest request) {
        CommentResponse response = commentService.addComment(id, request);
        return ResponseEntity
                .created(URI.create("/api/v1/tickets/" + id + "/comments/" + response.id()))
                .body(response);
    }
}
