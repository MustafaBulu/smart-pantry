package com.mustafabulu.smartpantry.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Product response")
public record ProductResponse(
        @Schema(description = "Product id", example = "10")
        Long id,
        @Schema(description = "Product name", example = "Potato Chips 150g")
        String name
) {
}
