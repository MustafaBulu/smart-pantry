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
        java.math.BigDecimal price,
        @Schema(description = "Migros Money price (if exists)", example = "89.90")
        java.math.BigDecimal moneyPrice,
        @Schema(description = "Basket threshold for campaign price", example = "50.00")
        java.math.BigDecimal basketDiscountThreshold,
        @Schema(description = "Campaign price when threshold is reached", example = "179.95")
        java.math.BigDecimal basketDiscountPrice,
        @Schema(description = "Campaign buy quantity for effective pricing", example = "2")
        Integer campaignBuyQuantity,
        @Schema(description = "Campaign pay quantity for effective pricing", example = "1")
        Integer campaignPayQuantity,
        @Schema(description = "Effective unit price with campaign", example = "100.00")
        java.math.BigDecimal effectivePrice
) {
}
