package com.example.kirotest.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Generic pagination envelope for list endpoints.
 *
 * <p>Mirrors the pagination contract defined in the API standards: the current page of
 * {@code data} plus the paging metadata needed by clients to render controls.
 *
 * @param data          the items on the current page
 * @param page          zero-based index of the current page
 * @param size          number of items requested per page
 * @param totalElements total number of matching items across all pages
 * @param totalPages    total number of pages available
 * @param <T>           element type of the page contents
 */
@Schema(description = "Pagination envelope wrapping a page of results")
public record PagedResponse<T>(

        @Schema(description = "Items on the current page")
        List<T> data,

        @Schema(description = "Zero-based current page index", example = "0")
        int page,

        @Schema(description = "Requested page size", example = "20")
        int size,

        @Schema(description = "Total matching items across all pages", example = "145")
        long totalElements,

        @Schema(description = "Total number of pages", example = "8")
        int totalPages
) {
}
