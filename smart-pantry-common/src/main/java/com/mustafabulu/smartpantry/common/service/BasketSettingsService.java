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

    private static final Pattern DECIMAL_PATTERN = Pattern.compile("(\\d[\\d.,]*)");
    private static final Logger log = LoggerFactory.getLogger(BasketSettingsService.class);
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_REFERER = "Referer";
    private static final String HEADER_X_FORWARDED_REST = "X-FORWARDED-REST";
    private static final String HEADER_X_PWA = "X-PWA";
    private static final String HEADER_X_DEVICE_PWA = "X-Device-PWA";
    private static final String HEADER_COOKIE = "Cookie";
    private static final String CONTENT_TYPE_JSON = "application/json";

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
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_REFERER, migrosCartReferer)
                //noinspection HttpHeaderName
                .header(HEADER_X_FORWARDED_REST, String.valueOf(migrosCartHeaderForwardedRest))
                //noinspection HttpHeaderName
                .header(HEADER_X_PWA, String.valueOf(migrosCartHeaderPwa))
                //noinspection HttpHeaderName
                .header(HEADER_X_DEVICE_PWA, String.valueOf(migrosCartHeaderDevicePwa))
                .header(HEADER_COOKIE, migrosCartCookie)
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
            BigDecimal resolvedYsMinimum = resolveOrZero(ysMinimumBasketAmount);
            BigDecimal resolvedMgMinimum = parsedMinBasket != null
                    ? parsedMinBasket
                    : resolveOrZero(mgMinimumBasketAmount);
            return new BasketMinimumSettingsResponse(resolvedYsMinimum, resolvedMgMinimum);
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
        String candidate = matcher.group(1).replaceAll("[^\\d,.-]", "");
        if (candidate.isBlank()) {
            return null;
        }

        int commaCount = (int) candidate.chars().filter(ch -> ch == ',').count();
        int dotCount = (int) candidate.chars().filter(ch -> ch == '.').count();
        candidate = normalizeSeparators(candidate, commaCount, dotCount);

        try {
            return new BigDecimal(candidate);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeSeparators(String candidate, int commaCount, int dotCount) {
        if (commaCount > 0 && dotCount > 0) {
            int lastComma = candidate.lastIndexOf(',');
            int lastDot = candidate.lastIndexOf('.');
            return lastComma > lastDot
                    ? candidate.replace(".", "").replace(",", ".")
                    : candidate.replace(",", "");
        }
        if (commaCount > 0) {
            return commaCount > 1 ? candidate.replace(",", "") : candidate.replace(",", ".");
        }
        if (dotCount > 1 || isThousandsDot(candidate)) {
            return candidate.replace(".", "");
        }
        return candidate;
    }

    private static boolean isThousandsDot(String candidate) {
        int dotIndex = candidate.indexOf('.');
        if (dotIndex < 0) {
            return false;
        }
        int digitsAfterDot = candidate.length() - dotIndex - 1;
        return digitsAfterDot == 3;
    }

    private static BigDecimal resolveOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
