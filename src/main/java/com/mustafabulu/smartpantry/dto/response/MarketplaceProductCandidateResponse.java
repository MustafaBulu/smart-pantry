package com.mustafabulu.smartpantry.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Marketplace product candidate")
public record MarketplaceProductCandidateResponse(
        @Schema(description = "Marketplace code", example = "YS")
        String marketplaceCode,
        @Schema(description = "Marketplace external id", example = "12345")
        String externalId,
        @Schema(description = "Product name", example = "XXX Chips 150g")
        String name,
        @Schema(description = "Brand name", example = "XXX")
        String brandName,
        @Schema(description = "Product list image url", example = "https://images.migrosone.com/sanalmarket/product/12500380/12500380-e81483-105x105.jpg")
        String imageUrl,
        @Schema(description = "Product price", example = "99.90")
        java.math.BigDecimal price
) {
}
