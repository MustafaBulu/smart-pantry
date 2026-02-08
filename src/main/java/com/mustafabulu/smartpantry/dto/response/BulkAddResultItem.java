package com.mustafabulu.smartpantry.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Bulk add result item")
public record BulkAddResultItem(
        @Schema(description = "Marketplace product id", example = "12345")
        String productId,
        @Schema(description = "HTTP status code", example = "201")
        int status,
        @Schema(description = "Result message", example = "Product added.")
        String message
) {
}
