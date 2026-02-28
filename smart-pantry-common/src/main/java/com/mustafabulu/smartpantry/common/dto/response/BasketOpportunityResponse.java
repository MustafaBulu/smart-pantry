package com.mustafabulu.smartpantry.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "General basket opportunity item")
public record BasketOpportunityResponse(
        @Schema(description = "Unique key", example = "MG:20000012500380")
        String key,
        @Schema(description = "Category id", example = "12")
        Long categoryId,
        @Schema(description = "Product name", example = "Kaymaksiz Yogurt 1000 G")
        String name,
        @Schema(description = "Marketplace code", example = "MG")
        String marketplaceCode,
        @Schema(description = "Marketplace external id", example = "20000012500380")
        String externalId,
        @Schema(description = "Internal product id", example = "42")
        Long productId,
        @Schema(description = "Availability status", example = "Uygun")
        String availabilityStatus,
        @Schema(description = "Opportunity level", example = "Yuksek")
        String opportunityLevel
) {
}
