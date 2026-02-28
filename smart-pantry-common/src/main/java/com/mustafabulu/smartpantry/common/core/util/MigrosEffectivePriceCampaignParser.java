package com.mustafabulu.smartpantry.common.core.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MigrosEffectivePriceCampaignParser {

    private static final Pattern BUY_PAY_PATTERN = Pattern.compile(
            "(\\d+)\\s*al\\s*(\\d+)\\s*ode",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PAY_REFUND_PATTERN = Pattern.compile(
            "(\\d+)\\s*ode\\s*(\\d+)\\s*'?i?(?:\\s*money\\s*(?:hediye|iade)|\\s*iade)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NTH_ITEM_PERCENT_DISCOUNT_PATTERN = Pattern.compile(
            "(\\d+)\\s*[.']?\\s*(?:si\\s*)?(?:urunde\\s*)?(?:%|yuzde)\\s*(\\d{1,2})\\s*indirim(?:li)?",
            Pattern.CASE_INSENSITIVE
    );

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
        Matcher matcher = BUY_PAY_PATTERN.matcher(tagText);
        if (!matcher.find()) {
            return null;
        }
        Integer buyQuantity = parsePositiveInt(matcher.group(1));
        Integer payQuantity = parsePositiveInt(matcher.group(2));
        if (buyQuantity == null || payQuantity == null || payQuantity > buyQuantity) {
            return null;
        }
        return new EffectivePriceCampaign(buyQuantity, payQuantity);
    }

    private static EffectivePriceCampaign parsePayRefund(String tagText) {
        Matcher matcher = PAY_REFUND_PATTERN.matcher(tagText);
        if (!matcher.find()) {
            return null;
        }
        Integer buyQuantity = parsePositiveInt(matcher.group(1));
        Integer refundQuantity = parsePositiveInt(matcher.group(2));
        if (buyQuantity == null || refundQuantity == null || refundQuantity >= buyQuantity) {
            return null;
        }
        return new EffectivePriceCampaign(buyQuantity, buyQuantity - refundQuantity);
    }

    private static EffectivePriceCampaign parseNthItemPercentDiscount(String tagText) {
        Matcher matcher = NTH_ITEM_PERCENT_DISCOUNT_PATTERN.matcher(tagText);
        if (!matcher.find()) {
            return null;
        }
        Integer nthItem = parsePositiveInt(matcher.group(1));
        Integer discountPercent = parsePositiveInt(matcher.group(2));
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
