package com.mustafabulu.smartpantry.migros.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.smartpantry.core.util.MigrosBasketDiscountParser;
import com.mustafabulu.smartpantry.core.util.MigrosEffectivePriceCampaignParser;
import com.mustafabulu.smartpantry.migros.model.MigrosProductDetails;
import com.mustafabulu.smartpantry.core.response.ResponseMessages;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import com.mustafabulu.smartpantry.core.exception.SPException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@AllArgsConstructor
public class MigrosScraperService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public MigrosProductDetails fetchProductDetails(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header(MigrosScraperConstants.USER_AGENT_HEADER, MigrosScraperConstants.USER_AGENT_VALUE)
                .build();

        try (Response response = new OkHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            String body = response.body().string();
            JsonNode root = objectMapper.readTree(body);
            JsonNode product = resolveProductNode(root);
            if (product.isMissingNode()) {
                return null;
            }

            String name = product.path(MigrosScraperConstants.NAME_KEY).asText();
            double price = resolvePrice(product);
            java.math.BigDecimal moneyPrice = resolveShownPrice(product);
            UnitInfo unitInfo = extractUnitInfo(product.path(MigrosScraperConstants.DESCRIPTION_KEY).asText(""));
            JsonNode brand = product.path(MigrosScraperConstants.BRAND_KEY);
            String brandString = brand.path(MigrosScraperConstants.BRAND_NAME_KEY).asText(null);
            if (brandString != null && brandString.isBlank()) {
                brandString = null;
            }
            MigrosBasketDiscountParser.BasketDiscount basketDiscount = resolveBasketDiscount(product);
            MigrosEffectivePriceCampaignParser.EffectivePriceCampaign campaign = resolveEffectiveCampaign(product);
            java.math.BigDecimal effectivePrice = resolveEffectivePrice(
                    java.math.BigDecimal.valueOf(price),
                    campaign
            );

            return new MigrosProductDetails(
                    name,
                    price,
                    unitInfo.unit(),
                    unitInfo.unitValue(),
                    brandString,
                    moneyPrice,
                    basketDiscount == null ? null : normalizePriceNode(basketDiscount.threshold()),
                    basketDiscount == null ? null : normalizePriceNode(basketDiscount.discountedPrice()),
                    campaign == null ? null : campaign.buyQuantity(),
                    campaign == null ? null : campaign.payQuantity(),
                    effectivePrice
            );
        } catch (IOException e) {
            throw new IllegalStateException(String.format(ResponseMessages.MIGROS_FETCH_FAILED, url), e);
        }
    }

    private JsonNode resolveProductNode(JsonNode root) {
        JsonNode successful = root.path(MigrosScraperConstants.SUCCESSFUL_KEY);
        if (successful.isBoolean() && !successful.asBoolean()) {
            throw new SPException(
                    HttpStatus.NOT_FOUND,
                    ResponseMessages.PRODUCT_NOT_FOUND,
                    ResponseMessages.PRODUCT_NOT_FOUND_CODE
            );
        }
        if (root.has(MigrosScraperConstants.DATA_KEY)) {
            JsonNode data = root.path(MigrosScraperConstants.DATA_KEY);

            if (data.has(MigrosScraperConstants.STORE_PRODUCT_INFO_KEY)) {
                return data.path(MigrosScraperConstants.STORE_PRODUCT_INFO_KEY);
            }
            return data;
        }
        return root;
    }

    private double resolvePrice(JsonNode product) {
        String[] keys = {
                MigrosScraperConstants.SALE_PRICE_KEY,
                MigrosScraperConstants.PRICE_KEY,
                MigrosScraperConstants.DISCOUNT_PRICE_KEY,
                MigrosScraperConstants.ORIGINAL_PRICE_KEY,
                MigrosScraperConstants.LIST_PRICE_KEY
        };
        for (String key : keys) {
            JsonNode node = product.path(key);
            if (node.isNumber()) {
                double value = node.asDouble();
                if (value > 0) {
                    return normalizePrice(value);
                }
            }
        }
        return 0;
    }

    private java.math.BigDecimal resolveShownPrice(JsonNode product) {
        String[] keys = {
                "shownPrice",
                "loyaltyPrice"
        };
        for (String key : keys) {
            JsonNode node = product.path(key);
            java.math.BigDecimal value = resolvePriceNode(node);
            if (value != null) {
                return value;
            }
            java.math.BigDecimal nestedValue = resolvePriceNode(node.path("value"));
            if (nestedValue != null) {
                return nestedValue;
            }
            java.math.BigDecimal nestedAmount = resolvePriceNode(node.path("amount"));
            if (nestedAmount != null) {
                return nestedAmount;
            }
        }
        return null;
    }

    private double normalizePrice(double value) {
        boolean integral = Math.rint(value) == value;
        if (integral && value >= 100) {
            return value / 100.0;
        }
        return value;
    }

    private java.math.BigDecimal resolvePriceNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        java.math.BigDecimal value = null;
        if (node.isNumber()) {
            value = java.math.BigDecimal.valueOf(node.asDouble());
        } else if (node.isTextual()) {
            String raw = node.asText("").trim().replace(",", ".");
            if (!raw.isBlank()) {
                try {
                    value = new java.math.BigDecimal(raw);
                } catch (NumberFormatException ignored) {
                    value = null;
                }
            }
        }
        return normalizePriceNode(value);
    }

    private java.math.BigDecimal normalizePriceNode(java.math.BigDecimal value) {
        if (value == null) {
            return null;
        }
        java.math.BigDecimal stripped = value.stripTrailingZeros();
        boolean integral = stripped.scale() <= 0;
        if (integral && stripped.abs().compareTo(java.math.BigDecimal.valueOf(100)) >= 0) {
            return stripped.movePointLeft(2);
        }
        return value;
    }

    private MigrosBasketDiscountParser.BasketDiscount resolveBasketDiscount(JsonNode product) {
        JsonNode discountTags = resolveDiscountTagsNode(product.path("crmDiscountTags"));
        if (!discountTags.isArray()) {
            return null;
        }
        for (JsonNode discountTag : discountTags) {
            String tagText = discountTag.path("tag").asText("");
            MigrosBasketDiscountParser.BasketDiscount parsed =
                    MigrosBasketDiscountParser.parse(tagText);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private JsonNode resolveDiscountTagsNode(JsonNode crmDiscountTagsNode) {
        if (crmDiscountTagsNode == null || crmDiscountTagsNode.isMissingNode() || crmDiscountTagsNode.isNull()) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
        if (crmDiscountTagsNode.isArray()) {
            return crmDiscountTagsNode;
        }
        return crmDiscountTagsNode.path("crmDiscountTags");
    }

    private MigrosEffectivePriceCampaignParser.EffectivePriceCampaign resolveEffectiveCampaign(JsonNode product) {
        MigrosEffectivePriceCampaignParser.EffectivePriceCampaign fromTags =
                parseEffectiveCampaignFromTextNodes(resolveDiscountTagsNode(product.path("crmDiscountTags")), "tag");
        if (fromTags != null) {
            return fromTags;
        }
        return parseEffectiveCampaignFromTextNodes(product.path("lists"), "name");
    }

    private MigrosEffectivePriceCampaignParser.EffectivePriceCampaign parseEffectiveCampaignFromTextNodes(
            JsonNode nodes,
            String fieldName
    ) {
        if (!nodes.isArray()) {
            return null;
        }
        for (JsonNode node : nodes) {
            String text = node.path(fieldName).asText("");
            MigrosEffectivePriceCampaignParser.EffectivePriceCampaign parsed =
                    MigrosEffectivePriceCampaignParser.parse(text);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private java.math.BigDecimal resolveEffectivePrice(
            java.math.BigDecimal price,
            MigrosEffectivePriceCampaignParser.EffectivePriceCampaign campaign
    ) {
        if (price == null || campaign == null) {
            return null;
        }
        java.math.BigDecimal buyQuantity = java.math.BigDecimal.valueOf(campaign.buyQuantity());
        java.math.BigDecimal payQuantity = java.math.BigDecimal.valueOf(campaign.payQuantity());
        return normalizePriceNode(price.multiply(payQuantity).divide(
                buyQuantity,
                2,
                java.math.RoundingMode.HALF_UP
        ));
    }

    private UnitInfo extractUnitInfo(String description) {
        if (description == null || description.isBlank()) {
            return new UnitInfo(null, null);
        }
        String normalized = description.replace("&nbsp;", " ");
        String rawValue = extractNetMiktarValue(normalized);
        if (rawValue == null || rawValue.isBlank()) {
            return new UnitInfo(null, null);
        }
        String lower = rawValue.toLowerCase();
        String unit = null;
        if (lower.contains("kg")) {
            unit = "g";
        } else if (lower.contains("g")) {
            unit = "g";
        } else if (lower.contains("ml")) {
            unit = "ml";
        } else if (lower.contains("l")) {
            unit = "ml";
        }

        String digits = rawValue.replaceAll("[^0-9.,]", "");
        if (digits.isBlank()) {
            return new UnitInfo(null, null);
        }
        digits = digits.replace(",", ".");
        try {
            double numeric = Double.parseDouble(digits);
            if ("ml".equals(unit) && lower.contains("l") && !lower.contains("ml")) {
                numeric *= 1000;
            }
            int unitValue = (int) Math.round(numeric);
            if (unit == null) {
                unit = "g";
            }
            return new UnitInfo(unit, unitValue);
        } catch (NumberFormatException e) {
            return new UnitInfo(null, null);
        }
    }

    private String extractNetMiktarValue(String description) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                MigrosScraperConstants.NET_MIKTAR_REGEX,
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher matcher = pattern.matcher(description);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private record UnitInfo(String unit, Integer unitValue) {
    }
}
