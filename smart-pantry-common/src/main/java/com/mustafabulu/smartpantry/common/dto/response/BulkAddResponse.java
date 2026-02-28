package com.mustafabulu.smartpantry.common.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Bulk add response")
public record BulkAddResponse(
        @Schema(description = "Total requested items", example = "2")
        int requested,
        @Schema(description = "Created items", example = "1")
        int created,
        @Schema(description = "Updated items", example = "1")
        int updated,
        @Schema(description = "Failed items", example = "0")
        int failed,
        @Schema(description = "Per-item results")
        List<BulkAddResultItem> results
) {
}
