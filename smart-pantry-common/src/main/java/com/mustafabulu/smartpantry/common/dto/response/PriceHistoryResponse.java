package com.mustafabulu.smartpantry.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Price history response")
public record PriceHistoryResponse(
        @Schema(description = "Price history id", example = "100")
        Long id,
        @Schema(description = "Product id", example = "10")
        Long productId,
        @Schema(description = "Product name", example = "Potato Chips 150g")
        String productName,
        @Schema(description = "Marketplace code", example = "MG")
        String marketplaceCode,
        @Schema(description = "Recorded price", example = "29.90")
        BigDecimal price,
        @Schema(description = "Price-based availability score (0-100)", example = "78.40")
        BigDecimal availabilityScore,
        @Schema(description = "Opportunity level derived from availability score", example = "Yuksek")
        String opportunityLevel,
        @Schema(description = "Recorded date", example = "2026-02-08")
        LocalDate recordedAt
) {
}
