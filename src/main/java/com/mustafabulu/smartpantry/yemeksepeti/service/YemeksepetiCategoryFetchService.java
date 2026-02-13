package com.mustafabulu.smartpantry.yemeksepeti.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.service.MarketplaceCategoryFetchService;
import lombok.extern.slf4j.Slf4j;
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

@Service
@Slf4j
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
    private static final String SEARCH_QUERY = """
            fragment ProductFields on Product {
                name
                price
                productID
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
            return new ArrayList<>(candidates.values());
        } catch (IOException ex) {
            log.warn("Yemeksepeti search response parse failed.", ex);
            return List.of();
        }
    }

    private String fetchSearchResponse(String query) {
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(buildPayload(query));
        } catch (IOException ex) {
            log.warn("Yemeksepeti search payload build failed.", ex);
            return null;
        }

        String timestampId = String.valueOf(System.currentTimeMillis());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SEARCH_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("apollographql-client-name", CLIENT_NAME)
                .header("apollographql-client-version", CLIENT_VERSION)
                .header("platform", PLATFORM)
                .header("perseus-client-id", timestampId)
                .header("perseus-session-id", timestampId)
                .header("cust-code", CUST_CODE)
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
            BigDecimal price = resolvePrice(payload.path("price"));
            MarketplaceProductCandidate candidate = new MarketplaceProductCandidate(
                    Marketplace.YS,
                    externalId,
                    name,
                    "",
                    "",
                    price
            );
            candidates.putIfAbsent(externalId, candidate);
        }
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
}
