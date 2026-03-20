package com.mustafabulu.smartpantry.yemeksepeti.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.service.MarketplaceCategoryFetchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

@Service
@Slf4j
@SuppressWarnings("HttpHeaderName")
@ConditionalOnProperty(prefix = "marketplace.ys", name = "enabled", havingValue = "true", matchIfMissing = true)
public class YemeksepetiCategoryFetchService implements MarketplaceCategoryFetchService {

    private static final String SEARCH_URL = "https://tr.fd-api.com/api/v5/graphql";
    private static final String CLIENT_NAME = "web";
    private static final String CLIENT_VERSION = "GROCERIES-MENU-MICROFRONTEND.26.06.0001";
    private static final String PLATFORM = "web";
    private static final String CUST_CODE = "tr212x84";
    private static final String BRAND = "ys";
    private static final String LANGUAGE_CODE = "tr";
    private static final String LOCALE = "tr_TR";
    private static final String VENDOR_ID = "j3a6";
    private static final int LIMIT = 12;
    private static final int MAX_PAGES = 40;
    private static final String HEADER_SEG_CLIENT = "client";
    private static final String HEADER_APOLLOGRAPHQL_CLIENT_NAME = buildHeaderName("apollographql", HEADER_SEG_CLIENT, "name");
    private static final String HEADER_APOLLOGRAPHQL_CLIENT_VERSION = buildHeaderName("apollographql", HEADER_SEG_CLIENT, "version");
    private static final String HEADER_PLATFORM = buildHeaderName("plat", "form");
    private static final String HEADER_PERSEUS_CLIENT_ID = buildHeaderName("perseus", HEADER_SEG_CLIENT, "id");
    private static final String HEADER_PERSEUS_SESSION_ID = buildHeaderName("perseus", "session", "id");
    private static final String HEADER_CUST_CODE = buildHeaderName("cust", "code");
    private static final Set<String> BRAND_ATTRIBUTE_KEYS = Set.of("brand", "brandName", "manufacturer", "producer");
    private static final Set<String> LEADING_SKIP_TOKENS = Set.of(
            "yeni", "indirim", "kampanya", "paket", "paketi", "bundle", "adet", "li", "lu", "x", "ve",
            "super", "mega", "ultra", "dev", "buyuk", "maxi", "mini"
    );
    private static final Set<String> LOCATION_PREFIX_TOKENS = Set.of("silivri", "susurluk", "gonen");
    private static final Set<String> KNOWN_MULTIWORD_BRANDS = Set.of(
            "la lorraine",
            "coca cola",
            "nuhun ankara",
            "kinder bueno"
    );
    private static final String SEARCH_QUERY = """
            fragment ProductFields on Product {
                name
                price
                productID
                urls
                attributes {
                    key
                    value
                }
                activeCampaigns {
                    type
                    discountType
                    discountValue
                    triggerQuantity
                    benefitQuantity
                }
            }
            
            query getSearchProducts(
                $brand: String!
                $languageCode: String!
                $limit: Int!
                $locale: String!
                $offset: Int!
                $query: String!
                $vendors: [VendorInformation!]!
                $verticalTypes: [String!]!
            ) {
                searchProducts(
                    input: {
                        brand: $brand
                        languageCode: $languageCode
                        limit: $limit
                        locale: $locale
                        offset: $offset
                        query: $query
                        vendors: $vendors
                        verticalTypes: $verticalTypes
                    }
                ) {
                    products {
                        items {
                            payload {
                                ...ProductFields
                            }
                        }
                    }
                }
            }
            """;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public YemeksepetiCategoryFetchService() {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .build(),
                new ObjectMapper()
        );
    }

    YemeksepetiCategoryFetchService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Marketplace marketplace() {
        return Marketplace.YS;
    }

    @Override
    public List<MarketplaceProductCandidate> fetchByCategory(String categoryName) {
        long startedAt = System.nanoTime();
        if (categoryName == null || categoryName.isBlank()) {
            return List.of();
        }
        String trimmed = categoryName.trim();
        Map<String, MarketplaceProductCandidate> candidates = new LinkedHashMap<>();
        int offset = 0;
        boolean shouldContinue = true;
        for (int page = 0; page < MAX_PAGES; page += 1) {
            if (!shouldContinue) {
                break;
            }
            PageCollectResult result = collectSearchPage(trimmed, offset, candidates);
            shouldContinue = result != null && !result.shouldStop();
            if (shouldContinue) {
                offset += LIMIT;
            }
        }
        List<MarketplaceProductCandidate> result = new ArrayList<>(candidates.values());
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "yemeksepeti candidate fetch timing: categoryName={}, count={}, durationMs={}",
                categoryName,
                result.size(),
                durationMs
        );
        return result;
    }

    private PageCollectResult collectSearchPage(
            String query,
            int offset,
            Map<String, MarketplaceProductCandidate> candidates
    ) {
        String responseBody = fetchSearchResponse(query, offset);
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        JsonNode root = parseSearchResponse(responseBody);
        if (root == null) {
            return null;
        }
        JsonNode searchProducts = root.path("data").path("searchProducts");
        if (searchProducts.isMissingNode() || searchProducts.isNull()) {
            logMissingSearchProducts(root, responseBody);
            return null;
        }
        JsonNode items = searchProducts.path("products").path("items");
        int beforeCount = candidates.size();
        collectCandidates(candidates, items);
        int pageItemCount = items.isArray() ? items.size() : 0;
        int addedCount = candidates.size() - beforeCount;
        boolean shouldStop = pageItemCount < LIMIT || addedCount <= 0;
        return new PageCollectResult(shouldStop);
    }

    private JsonNode parseSearchResponse(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (IOException ex) {
            log.warn("Yemeksepeti search response parse failed.", ex);
            return null;
        }
    }

    private void logMissingSearchProducts(JsonNode root, String responseBody) {
        JsonNode errors = root.path("errors");
        if (!errors.isMissingNode() && !errors.isNull()) {
            log.warn("Yemeksepeti search returned null data. errors={}", errors);
            return;
        }
        log.warn("Yemeksepeti search returned null data. bodySize={}", responseBody.length());
    }

    @SuppressWarnings("HttpHeaderName")
    private String fetchSearchResponse(String query, int offset) {
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(buildPayload(query, offset));
        } catch (IOException ex) {
            log.warn("Yemeksepeti search payload build failed.", ex);
            return null;
        }

        String timestampId = String.valueOf(System.currentTimeMillis());
        HttpRequest request = addCustomHeaders(HttpRequest.newBuilder(), timestampId)
                .uri(URI.create(SEARCH_URL))
                .timeout(Duration.ofSeconds(30))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Yemeksepeti search failed: status={}", response.statusCode());
                return null;
            }
            return response.body();
        } catch (IOException ex) {
            log.warn("Yemeksepeti search request failed.", ex);
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Yemeksepeti search request interrupted.", ex);
            return null;
        }
    }

    private ObjectNode buildPayload(String query, int offset) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("query", SEARCH_QUERY);

        ObjectNode variables = payload.putObject("variables");
        variables.put("brand", BRAND);
        variables.put("languageCode", LANGUAGE_CODE);
        variables.put("limit", LIMIT);
        variables.put("locale", LOCALE);
        variables.put("offset", Math.max(0, offset));
        variables.put("query", query);

        ArrayNode vendors = variables.putArray("vendors");
        ObjectNode vendor = objectMapper.createObjectNode();
        vendor.put("id", VENDOR_ID);
        vendors.add(vendor);

        ArrayNode verticalTypes = variables.putArray("verticalTypes");
        verticalTypes.add("darkstores");

        return payload;
    }

    private HttpRequest.Builder addCustomHeaders(HttpRequest.Builder builder, String timestampId) {
        return builder
                .header(HEADER_APOLLOGRAPHQL_CLIENT_NAME, CLIENT_NAME)
                .header(HEADER_APOLLOGRAPHQL_CLIENT_VERSION, CLIENT_VERSION)
                .header(HEADER_PLATFORM, PLATFORM)
                .header(HEADER_PERSEUS_CLIENT_ID, timestampId)
                .header(HEADER_PERSEUS_SESSION_ID, timestampId)
                .header(HEADER_CUST_CODE, CUST_CODE);
    }

    private static String buildHeaderName(String... parts) {
        return String.join("-", parts);
    }

    private void collectCandidates(
            Map<String, MarketplaceProductCandidate> candidates,
            JsonNode items
    ) {
        if (items == null || !items.isArray()) {
            return;
        }
        int loggedPayloadCount = 0;
        for (JsonNode item : items) {
            JsonNode payload = item.path("payload");
            if (loggedPayloadCount < 5) {
                log.info("YS search raw payload[{}]: {}", loggedPayloadCount, payload);
                loggedPayloadCount += 1;
            }
            String externalId = payload.path("productID").asText("");
            if (externalId.isBlank()) {
                continue;
            }
            String name = payload.path("name").asText("");
            ProductAttributeInfo attributeInfo = parseAttributeInfo(payload.path("attributes"));
            String brandName = resolveBrandName(name, attributeInfo.brandName());
            BigDecimal price = adjustBundlePrice(name, resolvePrice(payload.path("price")));
            String imageUrl = resolveImageUrl(payload.path("urls"));
            MarketplaceProductCandidate candidate = new MarketplaceProductCandidate(
                    Marketplace.YS,
                    externalId,
                    name,
                    brandName,
                    imageUrl,
                    price,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    attributeInfo.unit(),
                    attributeInfo.unitValue(),
                    attributeInfo.packCount()
            );
            candidates.putIfAbsent(externalId, candidate);
        }
    }

    private String resolveImageUrl(JsonNode urlsNode) {
        if (urlsNode == null || !urlsNode.isArray() || urlsNode.isEmpty()) {
            return "";
        }
        return urlsNode.get(0).asText("");
    }

    private BigDecimal resolvePrice(JsonNode priceNode) {
        if (priceNode == null || priceNode.isNull()) {
            return null;
        }
        if (priceNode.isNumber()) {
            return BigDecimal.valueOf(priceNode.asDouble());
        }
        return null;
    }

    private BigDecimal adjustBundlePrice(String name, BigDecimal price) {
        if (price == null || name == null || name.isBlank()) {
            return price;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        if (!normalized.contains("bundle")) {
            return price;
        }
        int multiplier = resolveBundleMultiplier(normalized);
        if (multiplier <= 1) {
            return price;
        }
        return price.multiply(BigDecimal.valueOf(multiplier));
    }

    private int resolveBundleMultiplier(String normalizedName) {
        String[] tokens = normalizedName.split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            int parsed = parseBundleCount(tokens, i);
            if (parsed > 1 && parsed < 10) {
                return parsed;
            }
        }
        // YS "Bundle" products are usually sold as 2-pack.
        return 2;
    }

    private ProductAttributeInfo parseAttributeInfo(JsonNode attributesNode) {
        if (attributesNode == null || !attributesNode.isArray()) {
            return new ProductAttributeInfo(null, null, null, null);
        }
        AttributeState state = new AttributeState();
        for (JsonNode attr : attributesNode) {
            applyAttributeValue(attr, state);
        }
        NormalizedAmount normalized = normalizeAmount(state.contentsValue, state.contentsUnit);
        if (normalized == null) {
            normalized = normalizeAmount(state.weightValue, state.weightUnit);
        }
        if (normalized != null && state.packCount != null && state.packCount > 1) {
            normalized = new NormalizedAmount(
                    normalized.unit(),
                    Math.toIntExact(Math.round((double) normalized.value() * state.packCount))
            );
        }
        return new ProductAttributeInfo(
                normalized == null ? null : normalized.unit(),
                normalized == null ? null : normalized.value(),
                state.packCount,
                state.brandName
        );
    }

    private void applyAttributeValue(JsonNode attr, AttributeState state) {
        String key = attr.path("key").asText("").trim();
        String value = attr.path("value").asText("").trim();
        if (key.isBlank() || value.isBlank()) {
            return;
        }
        switch (key) {
            case "contentsValue" -> state.contentsValue = parseDouble(value);
            case "contentsUnit" -> state.contentsUnit = value;
            case "weightValue" -> state.weightValue = parseDouble(value);
            case "weightUnit" -> state.weightUnit = value;
            case "numberOfUnits" -> state.packCount = normalizePackCount(parseInteger(value));
            default -> assignBrandIfEmpty(key, value, state);
        }
    }

    private void assignBrandIfEmpty(String key, String value, AttributeState state) {
        if (BRAND_ATTRIBUTE_KEYS.contains(key) && (state.brandName == null || state.brandName.isBlank())) {
            state.brandName = value;
        }
    }

    private Integer normalizePackCount(Integer value) {
        if (value == null || value <= 1) {
            return null;
        }
        return value;
    }

    private String resolveBrandName(String productName, String attributeBrand) {
        String fromAttribute = safeTrim(attributeBrand);
        if (!fromAttribute.isBlank()) {
            return canonicalizeBrandName(fromAttribute);
        }
        return canonicalizeBrandName(inferBrandFromName(productName));
    }

    private String inferBrandFromName(String productName) {
        if (productName == null || productName.isBlank()) {
            return "";
        }
        List<String> cleanedTokens = new ArrayList<>();
        for (String token : productName.trim().split("\\s+")) {
            String cleaned = cleanToken(token);
            if (!cleaned.isBlank()) {
                cleanedTokens.add(cleaned);
            }
        }
        if (cleanedTokens.isEmpty()) {
            return "";
        }

        int start = 0;
        while (start < cleanedTokens.size()) {
            String normalized = normalizeTokenForChecks(cleanedTokens.get(start));
            if (!isBundleOrCountToken(normalized) &&
                    !LEADING_SKIP_TOKENS.contains(normalized) &&
                    !LOCATION_PREFIX_TOKENS.contains(normalized)) {
                break;
            }
            start += 1;
        }
        if (start >= cleanedTokens.size()) {
            return "";
        }

        int maxPhraseLength = Math.min(3, cleanedTokens.size() - start);
        for (int len = maxPhraseLength; len >= 2; len -= 1) {
            String phraseKey = toBrandDictionaryKey(cleanedTokens, start, len);
            if (KNOWN_MULTIWORD_BRANDS.contains(phraseKey)) {
                return String.join(" ", cleanedTokens.subList(start, start + len));
            }
        }

        return cleanedTokens.get(start);
    }

    private String canonicalizeBrandName(String rawBrand) {
        String trimmed = safeTrim(rawBrand);
        if (trimmed.isBlank()) {
            return "";
        }
        String normalized = normalizeTokenForChecks(trimmed).replace("-", " ").replaceAll("\\s+", " ").trim();
        return switch (normalized) {
            case "coca", "coca cola", "coca kola" -> "Coca-Cola";
            default -> trimmed;
        };
    }

    private boolean isBundleOrCountToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return isAllDigits(token)
                || parseCompactXMultiplier(token) > 1
                || hasNumericPrefix(token, "x")
                || hasNumericSuffix(token, "x")
                || hasNumericSuffix(token, "li")
                || hasNumericSuffix(token, "lu");
    }

    private int parseBundleCount(String[] tokens, int index) {
        String token = tokens[index];
        int compactMultiplier = parseCompactXMultiplier(token);
        if (compactMultiplier > 1) {
            return compactMultiplier;
        }
        if (hasNumericPrefix(token, "x")) {
            return parseBoundedInt(token.substring(0, token.length() - 1));
        }
        if (hasNumericSuffix(token, "x")) {
            return parseBoundedInt(token.substring(1));
        }
        if (hasNumericSuffix(token, "li") || hasNumericSuffix(token, "lu")) {
            return parseBoundedInt(token.substring(0, token.length() - 2));
        }
        if (isAllDigits(token) && index + 1 < tokens.length) {
            String next = tokens[index + 1];
            if ("x".equals(next) || "li".equals(next) || "lu".equals(next)) {
                return parseBoundedInt(token);
            }
        }
        return 1;
    }

    private int parseCompactXMultiplier(String token) {
        int separatorIndex = token.toLowerCase(Locale.ROOT).indexOf('x');
        if (separatorIndex <= 0 || separatorIndex >= token.length() - 1) {
            return 1;
        }
        String left = token.substring(0, separatorIndex);
        String right = token.substring(separatorIndex + 1);
        if (!isAllDigits(left) || right.isBlank()) {
            return 1;
        }
        char firstRight = right.charAt(0);
        if (!Character.isDigit(firstRight)) {
            return 1;
        }
        return parseBoundedInt(left);
    }

    private boolean isAllDigits(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasNumericPrefix(String token, String suffix) {
        return token.length() > suffix.length()
                && token.endsWith(suffix)
                && isAllDigits(token.substring(0, token.length() - suffix.length()));
    }

    private boolean hasNumericSuffix(String token, String prefix) {
        return token.length() > prefix.length()
                && token.startsWith(prefix)
                && isAllDigits(token.substring(prefix.length()));
    }

    private int parseBoundedInt(String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private String cleanToken(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replaceAll("^[^\\p{L}\\p{N}]+", "")
                .replaceAll("[^\\p{L}\\p{N}]+$", "");
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeTokenForChecks(String token) {
        if (token == null) {
            return "";
        }
        return token
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replace("’", "")
                .replace("ç", "c")
                .replace("ğ", "g")
                .replace("ı", "i")
                .replace("ö", "o")
                .replace("ş", "s")
                .replace("ü", "u");
    }

    private String toBrandDictionaryKey(List<String> tokens, int start, int length) {
        List<String> normalized = new ArrayList<>();
        for (int i = start; i < start + length && i < tokens.size(); i += 1) {
            normalized.add(normalizeTokenForChecks(tokens.get(i)));
        }
        return String.join(" ", normalized);
    }

    private NormalizedAmount normalizeAmount(Double rawValue, String rawUnit) {
        if (rawValue == null || rawValue <= 0d || rawUnit == null || rawUnit.isBlank()) {
            return null;
        }
        String unit = rawUnit.trim().toLowerCase(Locale.ROOT);
        return switch (unit) {
            case "kg" -> new NormalizedAmount("g", (int) Math.round(rawValue * 1000d));
            case "g", "gr" -> new NormalizedAmount("g", (int) Math.round(rawValue));
            case "l", "lt" -> new NormalizedAmount("ml", (int) Math.round(rawValue * 1000d));
            case "ml" -> new NormalizedAmount("ml", (int) Math.round(rawValue));
            default -> null;
        };
    }

    private Double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.replace(",", "."));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer parseInteger(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record ProductAttributeInfo(String unit, Integer unitValue, Integer packCount, String brandName) {
    }

    private static final class AttributeState {
        private Double contentsValue;
        private String contentsUnit;
        private Double weightValue;
        private String weightUnit;
        private Integer packCount;
        private String brandName;
    }

    private record NormalizedAmount(String unit, Integer value) {
    }

    private record PageCollectResult(boolean shouldStop) {
    }
}



