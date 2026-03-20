package com.mustafabulu.smartpantry.common.core.util;

import java.math.BigDecimal;

public final class MigrosBasketDiscountParser {

    private MigrosBasketDiscountParser() {
    }

    public static BasketDiscount parse(String tagText) {
        if (tagText == null || tagText.isBlank()) {
            return null;
        }
        String lower = tagText.toLowerCase();
        int sepetteIndex = lower.indexOf("sepette");
        if (sepetteIndex < 0) {
            return null;
        }
        BigDecimal threshold = findLastDecimalBeforeTl(lower, sepetteIndex);
        BigDecimal discountedPrice = findFirstDecimalBeforeTl(lower, sepetteIndex + "sepette".length());
        if (threshold == null || discountedPrice == null) {
            return null;
        }
        return new BasketDiscount(threshold, discountedPrice);
    }

    private static BigDecimal findLastDecimalBeforeTl(String text, int endExclusive) {
        int tlIndex = text.lastIndexOf("tl", endExclusive);
        if (tlIndex < 0) {
            return null;
        }
        return parseDecimal(scanNumberBackward(text, tlIndex - 1));
    }

    private static BigDecimal findFirstDecimalBeforeTl(String text, int startInclusive) {
        int tlIndex = text.indexOf("tl", startInclusive);
        if (tlIndex < 0) {
            return null;
        }
        return parseDecimal(scanNumberBackward(text, tlIndex - 1));
    }

    private static String scanNumberBackward(String text, int startIndex) {
        int end = startIndex;
        while (end >= 0 && Character.isWhitespace(text.charAt(end))) {
            end--;
        }
        if (end < 0) {
            return "";
        }
        int start = end;
        while (start >= 0) {
            char current = text.charAt(start);
            if (!(Character.isDigit(current) || current == '.' || current == ',')) {
                break;
            }
            start--;
        }
        return text.substring(start + 1, end + 1);
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
