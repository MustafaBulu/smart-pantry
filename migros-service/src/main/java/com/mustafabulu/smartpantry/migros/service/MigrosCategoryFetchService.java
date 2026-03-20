package com.mustafabulu.smartpantry.migros.service;

import com.mustafabulu.smartpantry.common.core.util.MigrosBasketDiscountParser;
import com.mustafabulu.smartpantry.common.core.util.MigrosEffectivePriceCampaignParser;
import com.mustafabulu.smartpantry.common.config.MarketplaceUrlProperties;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.service.MarketplaceCatalogUrlFetchService;
import com.mustafabulu.smartpantry.common.service.MarketplaceCategorySeedService;
import com.mustafabulu.smartpantry.common.service.MarketplaceCategoryFetchService;
import com.mustafabulu.smartpantry.migros.constant.MigrosConstants;
import com.mustafabulu.smartpantry.migros.model.MigrosCatalogProduct;
import com.mustafabulu.smartpantry.migros.repository.MigrosCatalogProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "marketplace.mg", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigrosCategoryFetchService implements MarketplaceCategoryFetchService, MarketplaceCategorySeedService, MarketplaceCatalogUrlFetchService {

    private static final String SEARCH_URL =
            "https://www.migros.com.tr/rest/search/screens/products?q=%s&reid=1770560051246000048";
    private static final String TOP_LEVEL_CATEGORY_URL =
            "https://www.migros.com.tr/rest/categories/top-level?reid=1772491546503000001";
    private static final int MAX_SEARCH_PAGES = 500;
    private static final int MAX_DB_CANDIDATES = 500;
    private static final Pattern UNIT_PRICE_PATTERN =
            Pattern.compile("\\(([-0-9.,]+)\\s*TL\\s*/\\s*([A-Za-z]+)\\)");
    private static final Pattern COMBO_PACK_PATTERN =
            Pattern.compile("(\\d+)\\s*[xX]\\s*\\d+(?:[.,]\\d+)?\\s*(kg|gr|g|ml|lt|l)\\b");
    private static final Pattern QUANTITY_IN_NAME_PATTERN =
            Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(kg|gr|g|ml|lt|l)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern LIST_PACK_PATTERN =
            Pattern.compile("(\\d+)\\s*(?:['’]?(?:li|lı|lu|lü)|pack|paket)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final MarketplaceUrlProperties marketplaceUrlProperties;
    private final MigrosCatalogProductRepository migrosCatalogProductRepository;

    @Override
    public Marketplace marketplace() {
        return Marketplace.MG;
    }

    @Override
    public List<String> listSeedCategoryKeys() {
        Request request = new Request.Builder()
                .url(TOP_LEVEL_CATEGORY_URL)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Language", "tr")
                .addHeader("Referer", "https://www.migros.com.tr/")
                .addHeader("X-FORWARDED-REST", "true")
                .addHeader("X-PWA", "true")
                .addHeader("X-Device-PWA", "true")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return List.of();
            }
            Set<String> prettyNames = new LinkedHashSet<>();
            if (data.isArray()) {
                for (JsonNode item : data) {
                    String prettyName = item.path("prettyName").asText("").trim();
                    if (!prettyName.isBlank()) {
                        prettyNames.add(prettyName);
                    }
                }
            } else if (data.isObject()) {
                data.elements().forEachRemaining(item -> {
                    String prettyName = item.path("prettyName").asText("").trim();
                    if (!prettyName.isBlank()) {
                        prettyNames.add(prettyName);
                    }
                });
            }
            return new ArrayList<>(prettyNames);
        } catch (IOException ex) {
            log.warn("migros top-level category fetch failed.", ex);
            return List.of();
        }
    }

    @Override
    public List<CatalogUrlProductCandidate> fetchAllByUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return List.of();
        }
        Map<String, CatalogUrlProductCandidate> productsByExternalId = new LinkedHashMap<>();
        int page = 1;
        int pageCount = 1;
        while (page <= pageCount && page <= MAX_SEARCH_PAGES) {
            PageFetchResult pageFetchResult = fetchCatalogUrlPage(sourceUrl, page, pageCount, productsByExternalId);
            if (pageFetchResult == null) {
                break;
            }
            pageCount = pageFetchResult.nextPageCount();
            if (pageFetchResult.shouldStop()) {
                break;
            }
            page += 1;
        }
        return new ArrayList<>(productsByExternalId.values());
    }

    private PageFetchResult fetchCatalogUrlPage(
            String sourceUrl,
            int page,
            int pageCount,
            Map<String, CatalogUrlProductCandidate> productsByExternalId
    ) {
        String pagedUrl = withPageParam(sourceUrl, page);
        if (pagedUrl.isBlank()) {
            return null;
        }
        Request request = buildMigrosCatalogRequest(pagedUrl);
        try (Response response = httpClient.newCall(request).execute()) {
            JsonNode searchInfo = readSearchInfoNode(response);
            if (searchInfo == null) {
                return null;
            }
            int nextPageCount = Math.max(pageCount, searchInfo.path("pageCount").asInt(pageCount));
            JsonNode entries = searchInfo.path("storeProductInfos");
            int pageItemCount = collectCatalogUrlPageEntries(entries, productsByExternalId);
            log.info(
                    "migros catalog URL fetch page completed: page={}, pageCount={}, pageItemCount={}, uniqueProductCount={}",
                    page,
                    nextPageCount,
                    pageItemCount,
                    productsByExternalId.size()
            );
            boolean shouldStop = !entries.isArray() || entries.isEmpty();
            return new PageFetchResult(nextPageCount, shouldStop);
        } catch (IOException ex) {
            log.warn("migros catalog URL fetch failed: page={}, url={}", page, sourceUrl, ex);
            return null;
        }
    }

    private Request buildMigrosCatalogRequest(String pagedUrl) {
        return new Request.Builder()
                .url(pagedUrl)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Language", "tr")
                .addHeader("Referer", "https://www.migros.com.tr/")
                .addHeader("X-FORWARDED-REST", "true")
                .addHeader("X-PWA", "true")
                .addHeader("X-Device-PWA", "true")
                .build();
    }

    private JsonNode readSearchInfoNode(Response response) throws IOException {
        if (!response.isSuccessful() || response.body() == null) {
            return null;
        }
        JsonNode root = objectMapper.readTree(response.body().string());
        JsonNode dataNode = root.path("data");
        JsonNode searchInfo = dataNode.path("searchInfo");
        if (searchInfo.isMissingNode() || searchInfo.isNull()) {
            searchInfo = dataNode;
        }
        if (searchInfo.isMissingNode() || searchInfo.isNull()) {
            return null;
        }
        return searchInfo;
    }

    private int collectCatalogUrlPageEntries(
            JsonNode entries,
            Map<String, CatalogUrlProductCandidate> productsByExternalId
    ) {
        if (!entries.isArray()) {
            return 0;
        }
        Map<String, QuantityInfo> quantityByExternalId = new HashMap<>();
        for (JsonNode entry : entries) {
            String externalId = entry.path("id").asText("").trim();
            if (externalId.isBlank()) {
                continue;
            }
            String categoryName = extractCategoryName(entry);
            MarketplaceProductCandidate candidate = toCandidate(entry, categoryName, quantityByExternalId);
            if (candidate == null) {
                continue;
            }
            productsByExternalId.putIfAbsent(
                    externalId.toLowerCase(),
                    new CatalogUrlProductCandidate(categoryName, candidate)
            );
        }
        return entries.size();
    }

    @Override
    public List<MarketplaceProductCandidate> fetchByCategory(String categoryName) {
        long startedAt = System.nanoTime();
        if (categoryName == null || categoryName.isBlank()) {
            return List.of();
        }
        String keyword = categoryName.trim();
        String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
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
            Map<String, QuantityInfo> quantityByExternalId = new HashMap<>();
            if (entries.isArray()) {
                entries.forEach(entry -> {
                    MarketplaceProductCandidate candidate = toCandidate(entry, keyword, quantityByExternalId);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                });
            }
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(
                    "migros candidate fetch timing: categoryName={}, count={}, durationMs={}",
                    keyword,
                    candidates.size(),
                    durationMs
            );
            return candidates;
        } catch (IOException ex) {
            return List.of();
        }
    }

    private MarketplaceProductCandidate toCandidateFromCatalogRow(MigrosCatalogProduct row) {
        if (row == null || row.getExternalId() == null || row.getExternalId().isBlank()) {
            return null;
        }
        String productName = row.getProductName() == null ? "" : row.getProductName().trim();
        if (productName.isBlank()) {
            return null;
        }
        QuantityInfo quantityInfo = parseQuantityInfoFromCatalogRow(row);
        return new MarketplaceProductCandidate(
                Marketplace.MG,
                row.getExternalId().trim(),
                productName,
                resolveCatalogBrand(row),
                row.getImageUrl(),
                row.getRegularPrice(),
                row.getShownPrice(),
                null,
                null,
                null,
                null,
                null,
                quantityInfo.unit(),
                quantityInfo.amount() == null ? null : (int) Math.round(quantityInfo.amount()),
                quantityInfo.packCount()
        );
    }

    private QuantityInfo parseQuantityInfoFromCatalogRow(MigrosCatalogProduct row) {
        String productName = row.getProductName() == null ? "" : row.getProductName();
        Integer packCount = parsePackCountFromName(productName);

        QuantityInfo fromName = parseQuantityFromName(productName);
        if (fromName.amount() != null && fromName.unit() != null) {
            return new QuantityInfo(fromName.amount(), fromName.unit(), packCount);
        }
        return parseQuantityFromCatalogUnitPrice(row, packCount);
    }

    private QuantityInfo parseQuantityFromCatalogUnitPrice(MigrosCatalogProduct row, Integer packCount) {
        String unitPriceText = row.getUnitPrice();
        if (unitPriceText == null || unitPriceText.isBlank()) {
            return new QuantityInfo(null, null, packCount);
        }
        Matcher matcher = UNIT_PRICE_PATTERN.matcher(unitPriceText);
        if (!matcher.find()) {
            return new QuantityInfo(null, null, packCount);
        }
        Double unitRate = parseLocalizedDecimal(matcher.group(1));
        String rawPerUnit = matcher.group(2);
        String normalizedUnit = normalizeUnit(rawPerUnit);
        if (unitRate == null || unitRate <= 0d || normalizedUnit == null) {
            return new QuantityInfo(null, null, packCount);
        }
        BigDecimal referencePrice = row.getShownPrice() != null && row.getShownPrice().compareTo(BigDecimal.ZERO) > 0
                ? row.getShownPrice()
                : row.getRegularPrice();
        if (referencePrice == null || referencePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new QuantityInfo(null, null, packCount);
        }
        double multiplier = ("kg".equalsIgnoreCase(rawPerUnit) || "l".equalsIgnoreCase(rawPerUnit))
                ? 1000d
                : 1d;
        double resolved = (referencePrice.doubleValue() / unitRate) * multiplier;
        if (!Double.isFinite(resolved) || resolved <= 0d) {
            return new QuantityInfo(null, null, packCount);
        }
        return new QuantityInfo(resolved, normalizedUnit, packCount);
    }

    private QuantityInfo parseQuantityFromName(String productName) {
        if (productName == null || productName.isBlank()) {
            return new QuantityInfo(null, null, null);
        }
        Matcher matcher = QUANTITY_IN_NAME_PATTERN.matcher(productName.toLowerCase());
        Double amount = null;
        String unit = null;
        while (matcher.find()) {
            Double parsed = parseLocalizedDecimal(matcher.group(1));
            String normalized = normalizeUnit(matcher.group(2));
            if (parsed == null || normalized == null) {
                continue;
            }
            if ("g".equals(normalized) && "kg".equalsIgnoreCase(matcher.group(2))) {
                parsed = parsed * 1000d;
            } else if ("ml".equals(normalized)
                    && ("l".equalsIgnoreCase(matcher.group(2)) || "lt".equalsIgnoreCase(matcher.group(2)))) {
                parsed = parsed * 1000d;
            }
            amount = parsed;
            unit = normalized;
        }
        return new QuantityInfo(amount, unit, null);
    }

    private String inferBrandFromName(String productName) {
        if (productName == null || productName.isBlank()) {
            return "";
        }
        String[] tokens = productName.trim().split("\\s+");
        if (tokens.length == 0) {
            return "";
        }
        String token = tokens[0].replaceAll("[^\\p{L}\\p{N}]", "");
        return token == null ? "" : token.trim();
    }

    private String resolveCatalogBrand(MigrosCatalogProduct row) {
        if (row == null) {
            return "";
        }
        String stored = row.getBrandName();
        if (stored != null && !stored.isBlank()) {
            return stored.trim();
        }
        return inferBrandFromName(row.getProductName());
    }

    private List<String> tokenizeForSearch(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String normalized = normalizeSearchText(keyword);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String part : normalized.split(" ")) {
            String token = part.trim();
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean matchesSearchTokens(MigrosCatalogProduct row, List<String> queryTokens) {
        if (row == null) {
            return false;
        }
        if (queryTokens == null || queryTokens.isEmpty()) {
            return true;
        }
        Set<String> productWords = tokenizeToWordSet(row.getProductName());
        Set<String> brandWords = tokenizeToWordSet(row.getBrandName());
        if (productWords.isEmpty() && brandWords.isEmpty()) {
            return false;
        }
        for (String token : queryTokens) {
            boolean matched = startsWithAnyWord(productWords, token) || startsWithAnyWord(brandWords, token);
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private int scoreSearchMatch(MigrosCatalogProduct row, List<String> queryTokens) {
        if (row == null || queryTokens == null || queryTokens.isEmpty()) {
            return 0;
        }
        Set<String> productWords = tokenizeToWordSet(row.getProductName());
        Set<String> brandWords = tokenizeToWordSet(row.getBrandName());
        int score = 0;
        for (String token : queryTokens) {
            if (startsWithAnyWord(productWords, token)) {
                score += 3;
            } else if (startsWithAnyWord(brandWords, token)) {
                score += 2;
            }
        }
        return score;
    }

    private Set<String> tokenizeToWordSet(String text) {
        String normalized = normalizeSearchText(text);
        if (normalized.isBlank()) {
            return Set.of();
        }
        Set<String> words = new HashSet<>();
        for (String part : normalized.split(" ")) {
            String word = part.trim();
            if (word.length() >= 2) {
                words.add(word);
            }
        }
        return words;
    }

    private boolean startsWithAnyWord(Set<String> words, String token) {
        if (words == null || words.isEmpty() || token == null || token.isBlank()) {
            return false;
        }
        for (String word : words) {
            if (word.startsWith(token)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeSearchText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String lower = text.toLowerCase(Locale.forLanguageTag("tr-TR"));
        return lower
                .replace('ç', 'c')
                .replace('ğ', 'g')
                .replace('ı', 'i')
                .replace('ö', 'o')
                .replace('ş', 's')
                .replace('ü', 'u')
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
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

    private MarketplaceProductCandidate toCandidate(
            JsonNode entry,
            String categoryName,
            Map<String, QuantityInfo> quantityByExternalId
    ) {
        String externalId = entry.path("id").asText("");
        if (externalId.isBlank()) {
            return null;
        }
        String name = entry.path("name").asText("");
        String brandName = entry.path("brand").path("name").asText("");
        String imageUrl = resolveImageUrl(entry);
        BigDecimal price = resolvePrice(entry);
        BigDecimal moneyPrice = resolveShownPrice(entry);
        MigrosBasketDiscountParser.BasketDiscount basketDiscount = resolveBasketDiscount(entry);
        MigrosEffectivePriceCampaignParser.EffectivePriceCampaign campaign = resolveEffectiveCampaign(entry);
        BigDecimal effectivePrice = resolveEffectivePrice(price, campaign);
        QuantityInfo quantityInfo = parseQuantityInfoFromJson(entry, price, moneyPrice, externalId, quantityByExternalId);
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
                effectivePrice,
                quantityInfo.unit(),
                quantityInfo.amount() == null ? null : (int) Math.round(quantityInfo.amount()),
                quantityInfo.packCount()
        );
    }

    private String resolveImageUrl(JsonNode entry) {
        JsonNode images = entry.path("images");
        if (!images.isArray() || images.isEmpty()) {
            return "";
        }
        return images.get(0).path("urls").path("PRODUCT_LIST").asText("");
    }

    private String extractCategoryName(JsonNode entry) {
        String socialCategory = entry.path("socialProofInfo").path("categoryName").asText("").trim();
        if (!socialCategory.isBlank()) {
            return socialCategory;
        }
        String categoryName = entry.path("category").path("name").asText("").trim();
        if (!categoryName.isBlank()) {
            return categoryName;
        }
        return "Migros Kategori";
    }

    private String withPageParam(String sourceUrl, int page) {
        HttpUrl parsed = HttpUrl.parse(sourceUrl);
        if (parsed == null) {
            return "";
        }
        return parsed.newBuilder()
                .setQueryParameter("sayfa", String.valueOf(Math.max(1, page)))
                .build()
                .toString();
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

    private QuantityInfo parseQuantityInfoFromJson(
            JsonNode entry,
            BigDecimal price,
            BigDecimal moneyPrice,
            String externalId,
            Map<String, QuantityInfo> quantityByExternalId
    ) {
        Integer packCount = parsePackCountFromName(entry.path("name").asText(""));
        QuantityInfo fromPropertyInfos = parseQuantityFromPropertyInfos(entry);
        if (fromPropertyInfos.amount() != null && fromPropertyInfos.unit() != null) {
            return new QuantityInfo(fromPropertyInfos.amount(), fromPropertyInfos.unit(), packCount);
        }
        QuantityInfo fromProductDetails = resolveQuantityFromProductDetails(externalId, quantityByExternalId);

        String unitPriceText = entry.path("unitPrice").asText("");
        if (unitPriceText.isBlank()) {
            return withPackCountOrEmpty(fromProductDetails, packCount);
        }
        Matcher matcher = UNIT_PRICE_PATTERN.matcher(unitPriceText);
        if (!matcher.find()) {
            return withPackCountOrEmpty(fromProductDetails, packCount);
        }
        Double unitRate = parseLocalizedDecimal(matcher.group(1));
        String rawPerUnit = matcher.group(2);
        String normalizedUnit = normalizeUnit(rawPerUnit);
        if (unitRate == null || unitRate <= 0d || normalizedUnit == null) {
            return withPackCountOrEmpty(fromProductDetails, packCount);
        }
        BigDecimal referencePrice = moneyPrice != null && moneyPrice.compareTo(BigDecimal.ZERO) > 0 ? moneyPrice : price;
        if (referencePrice == null || referencePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return withPackCountOrEmpty(fromProductDetails, packCount);
        }
        double multiplier = ("kg".equalsIgnoreCase(rawPerUnit) || "l".equalsIgnoreCase(rawPerUnit))
                ? 1000d
                : 1d;
        double resolved = (referencePrice.doubleValue() / unitRate) * multiplier;
        if (!Double.isFinite(resolved) || resolved <= 0d) {
            return withPackCountOrEmpty(fromProductDetails, packCount);
        }
        return new QuantityInfo(resolved, normalizedUnit, packCount);
    }

    private QuantityInfo withPackCountOrEmpty(QuantityInfo source, Integer packCount) {
        if (source != null && source.amount() != null && source.unit() != null) {
            return new QuantityInfo(source.amount(), source.unit(), packCount);
        }
        return new QuantityInfo(null, null, packCount);
    }

    private QuantityInfo resolveQuantityFromProductDetails(
            String externalId,
            Map<String, QuantityInfo> quantityByExternalId
    ) {
        if (externalId == null || externalId.isBlank()) {
            return new QuantityInfo(null, null, null);
        }
        QuantityInfo cached = quantityByExternalId.get(externalId);
        if (cached != null) {
            return cached;
        }
        String detailsUrl = marketplaceUrlProperties.getMigrosPrefix()
                + externalId
                + marketplaceUrlProperties.getMigrosSuffix();
        Request request = new Request.Builder()
                .url(detailsUrl)
                .get()
                .build();
        QuantityInfo quantityInfo = new QuantityInfo(null, null, null);
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                quantityByExternalId.put(externalId, quantityInfo);
                return quantityInfo;
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            JsonNode detailsEntry = root.path("data").path("storeProductInfoDTO");
            if (detailsEntry.isMissingNode() || detailsEntry.isNull()) {
                detailsEntry = root.path("data");
            }
            quantityInfo = parseQuantityFromPropertyInfos(detailsEntry);
            quantityByExternalId.put(externalId, quantityInfo);
            return quantityInfo;
        } catch (IOException ignored) {
            quantityByExternalId.put(externalId, quantityInfo);
            return quantityInfo;
        }
    }

    private QuantityInfo parseQuantityFromPropertyInfos(JsonNode entry) {
        JsonNode propertyInfosMap = entry.path("propertyInfosMap");
        if (propertyInfosMap.isMissingNode() || propertyInfosMap.isNull()) {
            return new QuantityInfo(null, null, null);
        }
        JsonNode mainInfos = propertyInfosMap.path("MAIN");
        if (!mainInfos.isArray()) {
            return new QuantityInfo(null, null, null);
        }

        String rawValue = extractNetAmountRawValue(mainInfos);
        if (rawValue == null || rawValue.isBlank()) {
            return new QuantityInfo(null, null, null);
        }

        QuantityInfo direct = parseAmountWithUnit(rawValue);
        if (direct.amount() != null && direct.unit() != null) {
            return direct;
        }

        Double numeric = parseLocalizedDecimal(rawValue);
        if (numeric == null || numeric <= 0d) {
            return new QuantityInfo(null, null, null);
        }

        String normalizedFromUnitPrice = normalizeUnitFromUnitPrice(entry.path("unitPrice").asText(""));
        if (normalizedFromUnitPrice == null) {
            normalizedFromUnitPrice = "g";
        }
        return new QuantityInfo(numeric, normalizedFromUnitPrice, null);
    }

    private String extractNetAmountRawValue(JsonNode mainInfos) {
        for (JsonNode node : mainInfos) {
            String customId = node.path("customId").asText("").trim();
            String name = node.path("name").asText("").trim().toLowerCase();
            boolean netAmountField = "netKg".equalsIgnoreCase(customId) || name.contains("net miktar");
            if (!netAmountField) {
                continue;
            }
            String rawValue = node.path("value").asText("").trim();
            if (!rawValue.isBlank()) {
                return rawValue;
            }
        }
        return null;
    }

    private QuantityInfo parseAmountWithUnit(String rawValue) {
        String lower = rawValue.toLowerCase();
        if (lower.contains("kg")) {
            Double value = parseLocalizedDecimal(lower.replace("kg", "").trim());
            return value == null ? new QuantityInfo(null, null, null) : new QuantityInfo(value * 1000d, "g", null);
        }
        if (lower.contains("gr") || lower.contains(" g")) {
            String sanitized = lower.replace("gr", "").replace(" g", "").trim();
            Double value = parseLocalizedDecimal(sanitized);
            return value == null ? new QuantityInfo(null, null, null) : new QuantityInfo(value, "g", null);
        }
        if (lower.contains("ml")) {
            Double value = parseLocalizedDecimal(lower.replace("ml", "").trim());
            return value == null ? new QuantityInfo(null, null, null) : new QuantityInfo(value, "ml", null);
        }
        if (lower.contains("lt") || lower.contains(" l")) {
            String sanitized = lower.replace("lt", "").replace(" l", "").trim();
            Double value = parseLocalizedDecimal(sanitized);
            return value == null ? new QuantityInfo(null, null, null) : new QuantityInfo(value * 1000d, "ml", null);
        }
        return new QuantityInfo(null, null, null);
    }

    private Integer parsePackCountFromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String lower = name.toLowerCase();
        Matcher combo = COMBO_PACK_PATTERN.matcher(lower);
        if (combo.find()) {
            Integer parsed = parsePositiveInt(combo.group(1));
            if (parsed != null && parsed > 1) {
                return parsed;
            }
        }
        Matcher listed = LIST_PACK_PATTERN.matcher(lower);
        if (listed.find()) {
            Integer parsed = parsePositiveInt(listed.group(1));
            if (parsed != null && parsed > 1) {
                return parsed;
            }
        }
        return null;
    }

    private Integer parsePositiveInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeUnitFromUnitPrice(String unitPriceText) {
        if (unitPriceText == null || unitPriceText.isBlank()) {
            return null;
        }
        Matcher matcher = UNIT_PRICE_PATTERN.matcher(unitPriceText);
        if (!matcher.find()) {
            return null;
        }
        return normalizeUnit(matcher.group(2));
    }

    private Double parseLocalizedDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim();
        boolean hasComma = normalized.contains(",");
        boolean hasDot = normalized.contains(".");
        if (hasComma && hasDot) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else if (hasComma) {
            normalized = normalized.replace(",", ".");
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeUnit(String rawUnit) {
        if (rawUnit == null || rawUnit.isBlank()) {
            return null;
        }
        return switch (rawUnit.trim().toLowerCase()) {
            case "kg", "g", "gr" -> "g";
            case "l", "lt", "ml" -> "ml";
            default -> null;
        };
    }

    private record QuantityInfo(Double amount, String unit, Integer packCount) {
    }

    private record PageFetchResult(
            int nextPageCount,
            boolean shouldStop
    ) {
    }

}
