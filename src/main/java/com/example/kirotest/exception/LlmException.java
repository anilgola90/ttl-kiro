package com.example.kirotest.exception;

/**
 * Thrown when a call to the large language model (chat/generation) fails during the RAG
 * ask flow.
 *
 * <p>The {@code GlobalExceptionHandler} maps this exception to HTTP <b>500 Internal Server
 * Error</b> with error code {@code LLM_ERROR}, indicating an unexpected failure while
 * generating a grounded answer.
 */
public class LlmException extends RuntimeException {

    /**
     * Creates an exception describing an LLM generation failure.
     *
     * @param message a human-readable description of the failure
     * @param cause   the underlying cause of the failure
     */
    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
