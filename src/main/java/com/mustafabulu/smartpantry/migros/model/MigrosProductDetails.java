package com.mustafabulu.smartpantry.migros.model;

public record MigrosProductDetails(
        String name,
        double currentPrice,
        String unit,
        Integer unitValue,
        String brand
) {
}
