package com.mustafabulu.smartpantry.migros.model;

public record MigrosProductDetails(
        String name,
        double currentPrice,
        String unit,
        Integer unitValue,
        String brand,
        java.math.BigDecimal moneyPrice,
        java.math.BigDecimal basketDiscountThreshold,
        java.math.BigDecimal basketDiscountPrice,
        Integer campaignBuyQuantity,
        Integer campaignPayQuantity,
        java.math.BigDecimal effectivePrice
) {
}
