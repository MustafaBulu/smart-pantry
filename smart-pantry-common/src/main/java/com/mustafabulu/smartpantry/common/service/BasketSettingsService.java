package com.mustafabulu.smartpantry.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mustafabulu.smartpantry.common.core.util.MarketplacePriceNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.smartpantry.common.dto.response.BasketMinimumSettingsResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BasketSettingsService {

    private static final Pattern DECIMAL_PATTERN = Pattern.compile("([0-9][0-9.,]*)");
    private static final Logger log = LoggerFactory.getLogger(BasketSettingsService.class);

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    private final BigDecimal ysMinimumBasketAmount;
    private final BigDecimal mgMinimumBasketAmount;
    private final String migrosCartUrl;
    private final String migrosCartCookie;
    private final String migrosCartReferer;
    private final boolean migrosCartHeaderForwardedRest;
    private final boolean migrosCartHeaderPwa;
    private final boolean migrosCartHeaderDevicePwa;

    public BasketSettingsService(
            ObjectMapper objectMapper,
            OkHttpClient httpClient,
            @Value("${basket.minimum.ys:0}") BigDecimal ysMinimumBasketAmount,
            @Value("${basket.minimum.mg:0}") BigDecimal mgMinimumBasketAmount,
            @Value("${migros.cart.url:}") String migrosCartUrl,
            @Value("${migros.cart.cookie:}") String migrosCartCookie,
            @Value("${migros.cart.referer:https://www.migros.com.tr/sepetim}") String migrosCartReferer,
            @Value("${migros.cart.header.forwarded-rest:true}") boolean migrosCartHeaderForwardedRest,
            @Value("${migros.cart.header.pwa:true}") boolean migrosCartHeaderPwa,
            @Value("${migros.cart.header.device-pwa:true}") boolean migrosCartHeaderDevicePwa
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.ysMinimumBasketAmount = ysMinimumBasketAmount;
        this.mgMinimumBasketAmount = mgMinimumBasketAmount;
        this.migrosCartUrl = migrosCartUrl;
        this.migrosCartCookie = migrosCartCookie;
        this.migrosCartReferer = migrosCartReferer;
        this.migrosCartHeaderForwardedRest = migrosCartHeaderForwardedRest;
        this.migrosCartHeaderPwa = migrosCartHeaderPwa;
        this.migrosCartHeaderDevicePwa = migrosCartHeaderDevicePwa;
    }

    public BasketMinimumSettingsResponse getMinimumBasketSettings() {
        BasketMinimumSettingsResponse fromMigrosApi = fetchMigrosSettings();
        if (fromMigrosApi != null) {
            log.info(
                    "basket minimum resolved from Migros API: ys={}, mg={}",
                    fromMigrosApi.ysMinimumBasketAmount(),
                    fromMigrosApi.mgMinimumBasketAmount()
            );
            return fromMigrosApi;
        }
        BigDecimal fallbackYs = ysMinimumBasketAmount == null ? BigDecimal.ZERO : ysMinimumBasketAmount;
        BigDecimal fallbackMg = mgMinimumBasketAmount == null ? BigDecimal.ZERO : mgMinimumBasketAmount;
        log.warn(
                "basket minimum fallback used: ys={}, mg={} (check MIGROS_CART_COOKIE/MIGROS_CART_URL)",
                fallbackYs,
                fallbackMg
        );
        return new BasketMinimumSettingsResponse(
                fallbackYs,
                fallbackMg
        );
    }

    private BasketMinimumSettingsResponse fetchMigrosSettings() {
        if (migrosCartUrl == null || migrosCartUrl.isBlank() || migrosCartCookie == null || migrosCartCookie.isBlank()) {
            log.warn("Migros basket minimum fetch skipped: migrosCartUrl or migrosCartCookie is empty");
            return null;
        }
        Request request = new Request.Builder()
                .url(migrosCartUrl)
                .header("Accept", "application/json")
                .header("Referer", migrosCartReferer)
                .header("X-FORWARDED-REST", String.valueOf(migrosCartHeaderForwardedRest))
                .header("X-PWA", String.valueOf(migrosCartHeaderPwa))
                .header("X-Device-PWA", String.valueOf(migrosCartHeaderDevicePwa))
                .header("Cookie", migrosCartCookie)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("Migros basket minimum fetch failed: status={}, hasBody={}", response.code(), response.body() != null);
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            JsonNode cartInfo = root.path("data").path("cartInfo");
            if (cartInfo.isMissingNode() || cartInfo.isNull()) {
                log.warn("Migros basket minimum fetch failed: cartInfo missing in response");
                return null;
            }
            BigDecimal parsedMinBasket = normalizePriceValue(cartInfo.path("minimumRequiredRevenue"));
            if (parsedMinBasket == null) {
                log.warn("Migros basket minimum parse failed: minimumRequiredRevenue is missing or invalid");
            }
            return new BasketMinimumSettingsResponse(
                    ysMinimumBasketAmount == null ? BigDecimal.ZERO : ysMinimumBasketAmount,
                    parsedMinBasket == null ? (mgMinimumBasketAmount == null ? BigDecimal.ZERO : mgMinimumBasketAmount) : parsedMinBasket
            );
        } catch (IOException exception) {
            log.warn("Migros basket minimum fetch IO failure: {}", exception.getMessage());
            return null;
        }
    }

    private BigDecimal normalizePriceValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        BigDecimal numeric = null;
        if (node.isNumber()) {
            numeric = node.decimalValue();
        } else if (node.isTextual()) {
            numeric = parseLocalizedDecimal(node.asText(""));
        }
        if (numeric == null) {
            return null;
        }
        return MarketplacePriceNormalizer.normalizePotentialCents(numeric);
    }

    private BigDecimal parseLocalizedDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = DECIMAL_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        String candidate = matcher.group(1).replaceAll("[^0-9,\\.\\-]", "");
        if (candidate.isBlank()) {
            return null;
        }

        int commaCount = (int) candidate.chars().filter(ch -> ch == ',').count();
        int dotCount = (int) candidate.chars().filter(ch -> ch == '.').count();

        if (commaCount > 0 && dotCount > 0) {
            int lastComma = candidate.lastIndexOf(',');
            int lastDot = candidate.lastIndexOf('.');
            if (lastComma > lastDot) {
                candidate = candidate.replace(".", "").replace(",", ".");
            } else {
                candidate = candidate.replace(",", "");
            }
        } else if (commaCount > 0) {
            if (commaCount > 1) {
                candidate = candidate.replace(",", "");
            } else {
                candidate = candidate.replace(",", ".");
            }
        } else if (dotCount > 0) {
            if (dotCount > 1) {
                candidate = candidate.replace(".", "");
            } else {
                int dotIndex = candidate.indexOf('.');
                int digitsAfterDot = candidate.length() - dotIndex - 1;
                if (digitsAfterDot == 3) {
                    candidate = candidate.replace(".", "");
                }
            }
        }

        try {
            return new BigDecimal(candidate);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
