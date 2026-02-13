package com.mustafabulu.smartpantry.migros.service;

import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.service.MarketplaceCategoryFetchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class MigrosCategoryFetchService implements MarketplaceCategoryFetchService {

    private static final String SEARCH_URL =
            "https://www.migros.com.tr/rest/search/screens/products?q=%s&reid=1770560051246000048";

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Marketplace marketplace() {
        return Marketplace.MG;
    }

    @Override
    public List<MarketplaceProductCandidate> fetchByCategory(String categoryName) {
        String encoded = URLEncoder.encode(categoryName, StandardCharsets.UTF_8);
        Request request = new Request.Builder()
                .url(SEARCH_URL.formatted(encoded))
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return List.of();
            }
            String body = response.body().string();
            JsonNode root = objectMapper.readTree(body);
            JsonNode entries = root.path("data").path("searchInfo").path("storeProductInfos");
            List<MarketplaceProductCandidate> candidates = new ArrayList<>();
            if (entries.isArray()) {
                entries.forEach(entry -> {
                    String externalId = entry.path("id").asText("");
                    if (externalId.isBlank()) {
                        return;
                    }
                    String name = entry.path("name").asText("");
                    String brandName = entry.path("brand").path("name").asText("");
                    String imageUrl = "";
                    JsonNode images = entry.path("images");
                    if (images.isArray() && !images.isEmpty()) {
                        imageUrl = images.get(0).path("urls").path("PRODUCT_LIST").asText("");
                    }
                    BigDecimal price = resolvePrice(entry);
                    candidates.add(new MarketplaceProductCandidate(
                            Marketplace.MG,
                            externalId,
                            name,
                            brandName,
                            imageUrl,
                            price
                    ));
                });
            }
            return candidates;
        } catch (IOException ex) {
            return List.of();
        }
    }

    private BigDecimal resolvePrice(JsonNode entry) {
        BigDecimal direct = resolvePriceNode(entry.path("price"));
        if (direct != null) {
            return direct;
        }
        BigDecimal sale = resolvePriceNode(entry.path("salePrice"));
        if (sale != null) {
            return sale;
        }
        BigDecimal regular = resolvePriceNode(entry.path("regularPrice"));
        if (regular != null) {
            return regular;
        }
        BigDecimal discounted = resolvePriceNode(entry.path("discountedPrice"));
        if (discounted != null) {
            return discounted;
        }
        JsonNode priceNode = entry.path("price");
        BigDecimal value = resolvePriceNode(priceNode.path("value"));
        if (value != null) {
            return value;
        }
        return resolvePriceNode(priceNode.path("amount"));
    }

    private BigDecimal resolvePriceNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            BigDecimal value = BigDecimal.valueOf(node.asDouble());
            if (value.compareTo(BigDecimal.valueOf(1000)) >= 0
                    && value.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
                return value.movePointLeft(2);
            }
            return value;
        }
        return null;
    }
}
