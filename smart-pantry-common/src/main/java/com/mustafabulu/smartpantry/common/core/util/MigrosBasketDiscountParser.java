package com.mustafabulu.smartpantry.common.core.util;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MigrosBasketDiscountParser {

    private static final Pattern BASKET_DISCOUNT_PATTERN = Pattern.compile(
            "(\\d+[.,]?\\d*)\\s*TL\\s*Sepette\\s*(\\d+[.,]?\\d*)\\s*TL",
            Pattern.CASE_INSENSITIVE
    );

    private MigrosBasketDiscountParser() {
    }

    public static BasketDiscount parse(String tagText) {
        if (tagText == null || tagText.isBlank()) {
            return null;
        }
        Matcher matcher = BASKET_DISCOUNT_PATTERN.matcher(tagText);
        if (!matcher.find()) {
            return null;
        }
        BigDecimal threshold = parseDecimal(matcher.group(1));
        BigDecimal discountedPrice = parseDecimal(matcher.group(2));
        if (threshold == null || discountedPrice == null) {
            return null;
        }
        return new BasketDiscount(threshold, discountedPrice);
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().replace(",", ".");
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record BasketDiscount(BigDecimal threshold, BigDecimal discountedPrice) {
    }
}
