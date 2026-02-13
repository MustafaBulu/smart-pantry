package com.mustafabulu.smartpantry.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Marketplace product entry for a category")
public record MarketplaceProductEntryResponse(
        @Schema(description = "Marketplace code", example = "MG")
        String marketplaceCode,
        @Schema(description = "Marketplace external id", example = "20000012500380")
        String externalId,
        @Schema(description = "Product name", example = "Kaymaksiz Yogurt 1000 G")
        String name,
        @Schema(description = "Internal product id", example = "42")
        Long productId,
        @Schema(description = "Brand name", example = "Sutas")
        String brandName,
        @Schema(description = "Product list image url", example = "https://images.migrosone.com/sanalmarket/product/12500380/12500380-e81483-105x105.jpg")
        String imageUrl,
        @Schema(description = "Product price", example = "99.90")
        java.math.BigDecimal price
) {
}
