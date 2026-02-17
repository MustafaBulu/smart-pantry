package com.mustafabulu.smartpantry.core.util;

import com.mustafabulu.smartpantry.enums.Marketplace;

import java.math.BigDecimal;

public final class MarketplacePriceNormalizer {

    private MarketplacePriceNormalizer() {
    }

    public static BigDecimal normalizeForDisplay(Marketplace marketplace, BigDecimal price) {
        if (marketplace != Marketplace.MG || price == null) {
            return price;
        }
        BigDecimal stripped = price.stripTrailingZeros();
        boolean integral = stripped.scale() <= 0;
        if (integral && stripped.abs().compareTo(BigDecimal.valueOf(100)) >= 0) {
            return stripped.movePointLeft(2);
        }
        return price;
    }
}
