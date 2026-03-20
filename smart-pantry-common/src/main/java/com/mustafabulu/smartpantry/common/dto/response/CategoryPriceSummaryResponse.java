package com.mustafabulu.smartpantry.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Category price summary response")
public record CategoryPriceSummaryResponse(
        @Schema(description = "Product id", example = "10")
        Long productId,
        @Schema(description = "Product name", example = "Potato Chips 150g")
        String productName,
        @Schema(description = "Minimum price", example = "25.90")
        BigDecimal minPrice,
        @Schema(description = "Maximum price", example = "32.50")
        BigDecimal maxPrice,
        @Schema(description = "Average price", example = "29.20")
        BigDecimal avgPrice,
        @Schema(description = "Latest price", example = "29.90")
        BigDecimal lastPrice,
        @Schema(description = "Latest price date", example = "2026-02-08")
        LocalDate lastRecordedAt
) {
}
