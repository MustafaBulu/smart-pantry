package com.mustafabulu.smartpantry.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Product update request")
public record ProductUpdateRequest(
        @Schema(description = "New product name", example = "Potato Chips 200g")
        String name,
        @Schema(description = "New brand", example = "Lays")
        String brand,
        @Schema(description = "Unit label", example = "g")
        String unit,
        @Schema(description = "Unit value", example = "200")
        Integer unitValue,
        @Schema(description = "Target category name", example = "Snacks")
        String categoryName
) {
}
