package com.example.kirotest.service;

import com.example.kirotest.dto.request.AddCommentRequest;
import com.example.kirotest.dto.response.CommentResponse;

/**
 * Application service defining the comment use cases for a support ticket.
 *
 * <p>This interface is the single business-logic entry point for adding comments; the
 * controller layer depends only on this abstraction and never on the concrete
 * {@code CommentServiceImpl}. All methods accept and return DTOs — JPA entities are never
 * exposed across this boundary.
 */
public interface CommentService {

    /**
     * Adds a comment to an existing ticket.
     *
     * <p>Persists the comment against its owning ticket, touches the ticket's
     * {@code updatedAt} timestamp, and publishes a domain event so the embedding pipeline can
     * ingest the new comment asynchronously.
     *
     * @param ticketId the identifier of the ticket to comment on
     * @param request  the validated comment payload (body and author)
     * @return the persisted comment as a response projection
     */
    CommentResponse addComment(String ticketId, AddCommentRequest request);
}
