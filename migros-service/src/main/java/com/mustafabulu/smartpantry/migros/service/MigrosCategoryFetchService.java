package com.mustafabulu.smartpantry.migros.service;

import com.mustafabulu.smartpantry.common.core.util.MigrosBasketDiscountParser;
import com.mustafabulu.smartpantry.common.core.util.MigrosEffectivePriceCampaignParser;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.service.MarketplaceCategoryFetchService;
import com.mustafabulu.smartpantry.migros.constant.MigrosConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "marketplace.mg", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigrosCategoryFetchService implements MarketplaceCategoryFetchService {

    private static final String SEARCH_URL =
            "https://www.migros.com.tr/rest/search/screens/products?q=%s&reid=1770560051246000048";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

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
                    MarketplaceProductCandidate candidate = toCandidate(entry, categoryName);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
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
        BigDecimal direct = resolveFirstPriceNode(
                entry.path("price"),
                entry.path("salePrice"),
                entry.path("regularPrice"),
                entry.path("discountedPrice")
        );
        if (direct != null) {
            return direct;
        }
        JsonNode priceNode = entry.path("price");
        return resolveFirstPriceNode(
                priceNode.path(MigrosConstants.VALUE_KEY),
                priceNode.path(MigrosConstants.AMOUNT_KEY)
        );
    }

    private BigDecimal resolveShownPrice(JsonNode entry) {
        JsonNode shownPriceNode = entry.path("shownPrice");
        return resolveFirstPriceNode(
                shownPriceNode,
                shownPriceNode.path(MigrosConstants.VALUE_KEY),
                shownPriceNode.path(MigrosConstants.AMOUNT_KEY)
        );
    }

    private MigrosBasketDiscountParser.BasketDiscount resolveBasketDiscount(JsonNode entry) {
        JsonNode discountTags = MigrosJsonSupport.resolveDiscountTagsNode(
                entry.path(MigrosConstants.CRM_DISCOUNT_TAGS_KEY)
        );
        return MigrosJsonSupport.parseFromTextNodes(
                discountTags,
                MigrosConstants.TAG_KEY,
                MigrosBasketDiscountParser::parse
        );
    }

    private List<String> extractDiscountTagTexts(JsonNode entry) {
        JsonNode discountTags = MigrosJsonSupport.resolveDiscountTagsNode(
                entry.path(MigrosConstants.CRM_DISCOUNT_TAGS_KEY)
        );
        if (!discountTags.isArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (JsonNode discountTag : discountTags) {
            String tagText = discountTag.path(MigrosConstants.TAG_KEY).asText("").trim();
            if (!tagText.isBlank()) {
                tags.add(tagText);
            }
        }
        return tags;
    }

    private MigrosEffectivePriceCampaignParser.EffectivePriceCampaign resolveEffectiveCampaign(JsonNode entry) {
        return MigrosJsonSupport.resolveEffectiveCampaign(entry);
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
        return MigrosJsonSupport.normalizeMigrosPrice(price.multiply(payQuantity).divide(buyQuantity, 2, java.math.RoundingMode.HALF_UP));
    }

    private BigDecimal resolvePriceNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return MigrosJsonSupport.normalizeMigrosPrice(BigDecimal.valueOf(node.asDouble()));
        }
        if (!node.isTextual()) {
            return null;
        }
        String raw = node.asText("").trim().replace(",", ".");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return MigrosJsonSupport.normalizeMigrosPrice(new BigDecimal(raw));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private MarketplaceProductCandidate toCandidate(JsonNode entry, String categoryName) {
        String externalId = entry.path("id").asText("");
        if (externalId.isBlank()) {
            return null;
        }
        String name = entry.path("name").asText("");
        List<String> tagTexts = extractDiscountTagTexts(entry);
        log.info(
                "migros search product tags: categoryName={}, externalId={}, name={}, tags={}",
                categoryName,
                externalId,
                name,
                tagTexts
        );
        String brandName = entry.path("brand").path("name").asText("");
        String imageUrl = resolveImageUrl(entry);
        BigDecimal price = resolvePrice(entry);
        BigDecimal moneyPrice = resolveShownPrice(entry);
        MigrosBasketDiscountParser.BasketDiscount basketDiscount = resolveBasketDiscount(entry);
        MigrosEffectivePriceCampaignParser.EffectivePriceCampaign campaign = resolveEffectiveCampaign(entry);
        BigDecimal effectivePrice = resolveEffectivePrice(price, campaign);
        return new MarketplaceProductCandidate(
                Marketplace.MG,
                externalId,
                name,
                brandName,
                imageUrl,
                price,
                moneyPrice,
                basketDiscount == null ? null : MigrosJsonSupport.normalizeMigrosPrice(basketDiscount.threshold()),
                basketDiscount == null ? null : MigrosJsonSupport.normalizeMigrosPrice(basketDiscount.discountedPrice()),
                campaign == null ? null : campaign.buyQuantity(),
                campaign == null ? null : campaign.payQuantity(),
                effectivePrice
        );
    }

    private String resolveImageUrl(JsonNode entry) {
        JsonNode images = entry.path("images");
        if (!images.isArray() || images.isEmpty()) {
            return "";
        }
        return images.get(0).path("urls").path("PRODUCT_LIST").asText("");
    }

    private BigDecimal resolveFirstPriceNode(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            BigDecimal value = resolvePriceNode(node);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

}
