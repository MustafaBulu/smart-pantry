package com.mustafabulu.smartpantry.migros.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            UnitInfo unitInfo = extractUnitInfo(product.path(MigrosScraperConstants.DESCRIPTION_KEY).asText(""));
            JsonNode brand = product.path(MigrosScraperConstants.BRAND_KEY);
            String brandString = brand.path(MigrosScraperConstants.BRAND_NAME_KEY).asText(null);
            if (brandString != null && brandString.isBlank()) {
                brandString = null;
            }

            return new MigrosProductDetails(name, price, unitInfo.unit(), unitInfo.unitValue(), brandString);
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

    private double normalizePrice(double value) {
        if (value >= 1000) {
            return value / 100.0;
        }
        return value;
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
