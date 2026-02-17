package com.mustafabulu.smartpantry.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MigrosEffectivePriceCampaignParser {

    private static final Pattern BUY_PAY_PATTERN = Pattern.compile(
            "(\\d+)\\s*al\\s*(\\d+)\\s*(?:ode|öde)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PAY_REFUND_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:ode|öde)\\s*(\\d+)\\s*['’]?[iı]?(?:\\s*money\\s*(?:hediye|iade)|\\s*iade)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private MigrosEffectivePriceCampaignParser() {
    }

    public static EffectivePriceCampaign parse(String tagText) {
        if (tagText == null || tagText.isBlank()) {
            return null;
        }
        EffectivePriceCampaign buyPay = parseBuyPay(tagText);
        if (buyPay != null) {
            return buyPay;
        }
        return parsePayRefund(tagText);
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

    public record EffectivePriceCampaign(int buyQuantity, int payQuantity) {
    }
}
