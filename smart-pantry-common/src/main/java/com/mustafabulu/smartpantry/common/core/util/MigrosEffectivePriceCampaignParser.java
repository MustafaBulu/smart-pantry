package com.mustafabulu.smartpantry.common.core.util;

import java.text.Normalizer;
import java.util.Locale;

public final class MigrosEffectivePriceCampaignParser {

    private MigrosEffectivePriceCampaignParser() {
    }

    public static EffectivePriceCampaign parse(String tagText) {
        if (tagText == null || tagText.isBlank()) {
            return null;
        }
        String normalizedText = normalizeTagText(tagText);
        EffectivePriceCampaign buyPay = parseBuyPay(normalizedText);
        if (buyPay != null) {
            return buyPay;
        }
        EffectivePriceCampaign payRefund = parsePayRefund(normalizedText);
        if (payRefund != null) {
            return payRefund;
        }
        return parseNthItemPercentDiscount(normalizedText);
    }

    private static EffectivePriceCampaign parseBuyPay(String tagText) {
        Integer buyQuantity = firstPositiveIntBefore(tagText, "al");
        Integer payQuantity = firstPositiveIntAfter(tagText, "al", "ode");
        if (buyQuantity == null || payQuantity == null) {
            return null;
        }
        if (payQuantity > buyQuantity) {
            return null;
        }
        return new EffectivePriceCampaign(buyQuantity, payQuantity);
    }

    private static EffectivePriceCampaign parsePayRefund(String tagText) {
        Integer buyQuantity = firstPositiveIntBefore(tagText, "ode");
        Integer refundQuantity = firstPositiveIntAfter(tagText, "ode", "iade");
        if (refundQuantity == null && tagText.contains("money")) {
            refundQuantity = firstPositiveIntAfter(tagText, "ode", "money");
        }
        if (buyQuantity == null || refundQuantity == null) {
            return null;
        }
        if (refundQuantity >= buyQuantity) {
            return null;
        }
        return new EffectivePriceCampaign(buyQuantity, buyQuantity - refundQuantity);
    }

    private static EffectivePriceCampaign parseNthItemPercentDiscount(String tagText) {
        int percentMarker = tagText.indexOf('%');
        if (percentMarker < 0) {
            percentMarker = tagText.indexOf("yuzde");
        }
        int discountIndex = tagText.indexOf("indirim");
        if (percentMarker < 0 || discountIndex < 0 || percentMarker > discountIndex) {
            return null;
        }
        Integer nthItem = firstPositiveIntBefore(tagText, percentMarker);
        Integer discountPercent = firstPositiveIntBetween(tagText, percentMarker, discountIndex);
        if (nthItem == null || nthItem < 2 || discountPercent == null || discountPercent <= 0 || discountPercent >= 100) {
            return null;
        }

        int buyQuantity = nthItem * 100;
        int payQuantity = buyQuantity - discountPercent;
        int gcd = greatestCommonDivisor(buyQuantity, payQuantity);
        return new EffectivePriceCampaign(buyQuantity / gcd, payQuantity / gcd);
    }

    private static Integer parsePositiveInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer firstPositiveIntBefore(String text, String marker) {
        int markerIndex = text.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        return firstPositiveIntBefore(text, markerIndex);
    }

    private static Integer firstPositiveIntBefore(String text, int endExclusive) {
        String token = scanNumberBackward(text, endExclusive - 1);
        return parsePositiveInt(token);
    }

    private static Integer firstPositiveIntAfter(String text, String afterMarker, String beforeMarker) {
        int afterIndex = text.indexOf(afterMarker);
        if (afterIndex < 0) {
            return null;
        }
        int start = afterIndex + afterMarker.length();
        int end = text.indexOf(beforeMarker, start);
        if (end < 0) {
            return null;
        }
        return firstPositiveIntBetween(text, start, end);
    }

    private static Integer firstPositiveIntBetween(String text, int startInclusive, int endExclusive) {
        String token = scanNumberForward(text, startInclusive, endExclusive);
        return parsePositiveInt(token);
    }

    private static String scanNumberBackward(String text, int startIndex) {
        int end = startIndex;
        while (end >= 0 && !Character.isDigit(text.charAt(end))) {
            end--;
        }
        if (end < 0) {
            return "";
        }
        int start = end;
        while (start >= 0 && Character.isDigit(text.charAt(start))) {
            start--;
        }
        return text.substring(start + 1, end + 1);
    }

    private static String scanNumberForward(String text, int startInclusive, int endExclusive) {
        int start = startInclusive;
        while (start < endExclusive && !Character.isDigit(text.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < endExclusive && Character.isDigit(text.charAt(end))) {
            end++;
        }
        return start < end ? text.substring(start, end) : "";
    }

    private static int greatestCommonDivisor(int left, int right) {
        int a = Math.abs(left);
        int b = Math.abs(right);
        while (b != 0) {
            int temp = a % b;
            a = b;
            b = temp;
        }
        return a == 0 ? 1 : a;
    }

    private static String normalizeTagText(String tagText) {
        String lower = tagText.toLowerCase(Locale.ROOT).replace('ı', 'i').replace('’', '\'');
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }

    public record EffectivePriceCampaign(int buyQuantity, int payQuantity) {
    }
}
