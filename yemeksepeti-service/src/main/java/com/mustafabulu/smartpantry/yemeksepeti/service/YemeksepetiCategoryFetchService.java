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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final String HEADER_SEG_CLIENT = "client";
    private static final String HEADER_APOLLOGRAPHQL_CLIENT_NAME = buildHeaderName("apollographql", HEADER_SEG_CLIENT, "name");
    private static final String HEADER_APOLLOGRAPHQL_CLIENT_VERSION = buildHeaderName("apollographql", HEADER_SEG_CLIENT, "version");
    private static final String HEADER_PLATFORM = buildHeaderName("plat", "form");
    private static final String HEADER_PERSEUS_CLIENT_ID = buildHeaderName("perseus", HEADER_SEG_CLIENT, "id");
    private static final String HEADER_PERSEUS_SESSION_ID = buildHeaderName("perseus", "session", "id");
    private static final String HEADER_CUST_CODE = buildHeaderName("cust", "code");
    private static final Pattern EXPLICIT_MULTIPLIER_PATTERN = Pattern.compile(
            "(\\d+)\\s*x|x\\s*(\\d+)|(\\d+)\\s*(li|lu|lü)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.CANON_EQ
    );
    private static final String SEARCH_QUERY = """
            fragment ProductFields on Product {
                name
                price
                productID
                urls
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

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        String responseBody = fetchSearchResponse(trimmed);
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode searchProducts = root.path("data").path("searchProducts");
            if (searchProducts.isMissingNode() || searchProducts.isNull()) {
                JsonNode errors = root.path("errors");
                if (!errors.isMissingNode() && !errors.isNull()) {
                    log.warn("Yemeksepeti search returned null data. errors={}", errors);
                } else {
                    log.warn("Yemeksepeti search returned null data. bodySize={}", responseBody.length());
                }
                return List.of();
            }
            Map<String, MarketplaceProductCandidate> candidates = new LinkedHashMap<>();
            collectCandidates(candidates, searchProducts.path("products").path("items"));
            List<MarketplaceProductCandidate> result = new ArrayList<>(candidates.values());
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(
                    "yemeksepeti candidate fetch timing: categoryName={}, count={}, durationMs={}",
                    categoryName,
                    result.size(),
                    durationMs
            );
            return result;
        } catch (IOException ex) {
            log.warn("Yemeksepeti search response parse failed.", ex);
            return List.of();
        }
    }

    @SuppressWarnings("HttpHeaderName")
    private String fetchSearchResponse(String query) {
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(buildPayload(query));
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

    private ObjectNode buildPayload(String query) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("query", SEARCH_QUERY);

        ObjectNode variables = payload.putObject("variables");
        variables.put("brand", BRAND);
        variables.put("languageCode", LANGUAGE_CODE);
        variables.put("limit", LIMIT);
        variables.put("locale", LOCALE);
        variables.put("offset", 0);
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
        for (JsonNode item : items) {
            JsonNode payload = item.path("payload");
            String externalId = payload.path("productID").asText("");
            if (externalId.isBlank()) {
                continue;
            }
            String name = payload.path("name").asText("");
            BigDecimal price = adjustBundlePrice(name, resolvePrice(payload.path("price")));
            String imageUrl = resolveImageUrl(payload.path("urls"));
            MarketplaceProductCandidate candidate = new MarketplaceProductCandidate(
                    Marketplace.YS,
                    externalId,
                    name,
                    "",
                    imageUrl,
                    price,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
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
        Matcher matcher = EXPLICIT_MULTIPLIER_PATTERN.matcher(normalizedName);
        if (matcher.find()) {
            for (int i = 1; i <= 3; i++) {
                String group = matcher.group(i);
                if (group == null || group.isBlank()) {
                    continue;
                }
                try {
                    int parsed = Integer.parseInt(group);
                    if (parsed > 1 && parsed < 10) {
                        return parsed;
                    }
                } catch (NumberFormatException ignored) {
                    // Continue with fallback multiplier.
                }
            }
        }
        // YS "Bundle" products are usually sold as 2-pack.
        return 2;
    }
}

