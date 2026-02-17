package com.mustafabulu.smartpantry.migros.service;

import com.mustafabulu.smartpantry.core.util.MigrosBasketDiscountParser;
import com.mustafabulu.smartpantry.core.util.MigrosEffectivePriceCampaignParser;
import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.service.MarketplaceCategoryFetchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        long startedAt = System.nanoTime();
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
                    BigDecimal moneyPrice = resolveShownPrice(entry);
                    MigrosBasketDiscountParser.BasketDiscount basketDiscount = resolveBasketDiscount(entry);
                    MigrosEffectivePriceCampaignParser.EffectivePriceCampaign campaign =
                            resolveEffectiveCampaign(entry);
                    BigDecimal effectivePrice = resolveEffectivePrice(price, campaign);
                    candidates.add(new MarketplaceProductCandidate(
                            Marketplace.MG,
                            externalId,
                            name,
                            brandName,
                            imageUrl,
                            price,
                            moneyPrice,
                            basketDiscount == null ? null : normalizeMigrosPrice(basketDiscount.threshold()),
                            basketDiscount == null ? null : normalizeMigrosPrice(basketDiscount.discountedPrice()),
                            campaign == null ? null : campaign.buyQuantity(),
                            campaign == null ? null : campaign.payQuantity(),
                            effectivePrice
                    ));
                });
            }
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(
                    "migros candidate fetch timing: categoryName={}, count={}, durationMs={}",
                    categoryName,
                    candidates.size(),
                    durationMs
            );
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

    private BigDecimal resolveShownPrice(JsonNode entry) {
        BigDecimal shownPrice = resolvePriceNode(entry.path("shownPrice"));
        if (shownPrice != null) {
            return shownPrice;
        }
        JsonNode shownPriceNode = entry.path("shownPrice");
        BigDecimal shownPriceValue = resolvePriceNode(shownPriceNode.path("value"));
        if (shownPriceValue != null) {
            return shownPriceValue;
        }
        return resolvePriceNode(shownPriceNode.path("amount"));
    }

    private MigrosBasketDiscountParser.BasketDiscount resolveBasketDiscount(JsonNode entry) {
        JsonNode discountTags = resolveDiscountTagsNode(entry.path("crmDiscountTags"));
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

    private MigrosEffectivePriceCampaignParser.EffectivePriceCampaign resolveEffectiveCampaign(JsonNode entry) {
        MigrosEffectivePriceCampaignParser.EffectivePriceCampaign fromTags =
                parseEffectiveCampaignFromTextNodes(resolveDiscountTagsNode(entry.path("crmDiscountTags")), "tag");
        if (fromTags != null) {
            return fromTags;
        }
        return parseEffectiveCampaignFromTextNodes(entry.path("lists"), "name");
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

    private BigDecimal resolveEffectivePrice(
            BigDecimal price,
            MigrosEffectivePriceCampaignParser.EffectivePriceCampaign campaign
    ) {
        if (price == null || campaign == null) {
            return null;
        }
        BigDecimal buyQuantity = BigDecimal.valueOf(campaign.buyQuantity());
        BigDecimal payQuantity = BigDecimal.valueOf(campaign.payQuantity());
        return normalizeMigrosPrice(price.multiply(payQuantity).divide(buyQuantity, 2, java.math.RoundingMode.HALF_UP));
    }

    private BigDecimal resolvePriceNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        BigDecimal value = null;
        if (node.isNumber()) {
            value = BigDecimal.valueOf(node.asDouble());
        } else if (node.isTextual()) {
            String raw = node.asText("").trim().replace(",", ".");
            if (!raw.isBlank()) {
                try {
                    value = new BigDecimal(raw);
                } catch (NumberFormatException ignored) {
                    value = null;
                }
            }
        }
        return normalizeMigrosPrice(value);
    }

    private BigDecimal normalizeMigrosPrice(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        boolean integral = stripped.scale() <= 0;
        if (integral && stripped.abs().compareTo(BigDecimal.valueOf(100)) >= 0) {
            return stripped.movePointLeft(2);
        }
        return value;
    }
}
