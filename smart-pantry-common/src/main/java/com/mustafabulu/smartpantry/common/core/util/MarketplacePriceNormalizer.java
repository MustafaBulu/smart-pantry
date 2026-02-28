package com.mustafabulu.smartpantry.common.core.util;

import com.mustafabulu.smartpantry.common.enums.Marketplace;

import java.math.BigDecimal;

public final class MarketplacePriceNormalizer {

    private MarketplacePriceNormalizer() {
    }

    public static BigDecimal normalizePotentialCents(BigDecimal price) {
        if (price == null) {
            return null;
        }
        BigDecimal stripped = price.stripTrailingZeros();
        boolean integral = stripped.scale() <= 0;
        if (integral && stripped.abs().compareTo(BigDecimal.valueOf(100)) >= 0) {
            return stripped.movePointLeft(2);
        }
        return price;
    }

    public static BigDecimal normalizeForDisplay(Marketplace marketplace, BigDecimal price) {
        // Migros prices are already normalized in scraping/fetch layers.
        // Applying another divide-by-100 here causes incorrect values like 157 -> 1.57.
        return price;
    }
}
