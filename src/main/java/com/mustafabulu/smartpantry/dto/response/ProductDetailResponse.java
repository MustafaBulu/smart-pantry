package com.mustafabulu.smartpantry.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Product detail response")
public record ProductDetailResponse(
        @Schema(description = "Product id", example = "10")
        Long id,
        @Schema(description = "Product name", example = "Potato Chips 150g")
        String name,
        @Schema(description = "Brand name", example = "Lays")
        String brand,
        @Schema(description = "Unit label", example = "g")
        String unit,
        @Schema(description = "Unit value", example = "150")
        Integer unitValue,
        @Schema(description = "Category id", example = "1")
        Long categoryId,
        @Schema(description = "Category name", example = "Snacks")
        String categoryName,
        @Schema(description = "Creation timestamp", example = "2026-02-08T10:15:30")
        LocalDateTime createdAt
) {
}
