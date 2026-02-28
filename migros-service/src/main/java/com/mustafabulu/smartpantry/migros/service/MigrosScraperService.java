package com.mustafabulu.smartpantry.migros.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.smartpantry.common.core.util.MigrosBasketDiscountParser;
import com.mustafabulu.smartpantry.common.core.util.MigrosEffectivePriceCampaignParser;
import com.mustafabulu.smartpantry.migros.model.MigrosProductDetails;
import com.mustafabulu.smartpantry.migros.constant.MigrosConstants;
import com.mustafabulu.smartpantry.common.core.response.ResponseMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import com.mustafabulu.smartpantry.common.core.exception.SPException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class MigrosScraperService {
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public MigrosProductDetails fetchProductDetails(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header(MigrosScraperConstants.USER_AGENT_HEADER, MigrosScraperConstants.USER_AGENT_VALUE)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
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
                    basketDiscount == null ? null : MigrosJsonSupport.normalizeMigrosPrice(basketDiscount.threshold()),
                    basketDiscount == null ? null : MigrosJsonSupport.normalizeMigrosPrice(basketDiscount.discountedPrice()),
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
            java.math.BigDecimal resolved = resolveFirstPriceNode(
                    node,
                    node.path(MigrosConstants.VALUE_KEY),
                    node.path(MigrosConstants.AMOUNT_KEY)
            );
            if (resolved != null) {
                return resolved;
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
        if (node.isNumber()) {
            return MigrosJsonSupport.normalizeMigrosPrice(java.math.BigDecimal.valueOf(node.asDouble()));
        }
        if (!node.isTextual()) {
            return null;
        }
        String raw = node.asText("").trim().replace(",", ".");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return MigrosJsonSupport.normalizeMigrosPrice(new java.math.BigDecimal(raw));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private MigrosBasketDiscountParser.BasketDiscount resolveBasketDiscount(JsonNode product) {
        JsonNode discountTags = MigrosJsonSupport.resolveDiscountTagsNode(
                product.path(MigrosConstants.CRM_DISCOUNT_TAGS_KEY),
                MigrosConstants.TAG_KEY
        );
        return MigrosJsonSupport.parseFromTextNodes(
                discountTags,
                MigrosConstants.TAG_KEY,
                MigrosBasketDiscountParser::parse
        );
    }

    private MigrosEffectivePriceCampaignParser.EffectivePriceCampaign resolveEffectiveCampaign(JsonNode product) {
        return MigrosJsonSupport.resolveEffectiveCampaign(product);
    }

    private java.math.BigDecimal resolveFirstPriceNode(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            java.math.BigDecimal value = resolvePriceNode(node);
            if (value != null) {
                return value;
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
        return MigrosJsonSupport.normalizeMigrosPrice(price.multiply(payQuantity).divide(
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
