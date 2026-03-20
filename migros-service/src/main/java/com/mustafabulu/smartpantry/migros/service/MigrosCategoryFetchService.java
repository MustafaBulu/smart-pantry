package com.mustafabulu.smartpantry.migros.service;

import com.mustafabulu.smartpantry.common.core.util.MigrosBasketDiscountParser;
import com.mustafabulu.smartpantry.common.core.util.MigrosEffectivePriceCampaignParser;
import com.mustafabulu.smartpantry.common.config.MarketplaceUrlProperties;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.service.MarketplaceCatalogUrlFetchService;
import com.mustafabulu.smartpantry.common.service.MarketplaceCategorySeedService;
import com.mustafabulu.smartpantry.common.service.MarketplaceCategoryFetchService;
import com.mustafabulu.smartpantry.migros.constant.MigrosConstants;
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
@SuppressWarnings("java:S5854")
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
    private static final Pattern QUANTITY_IN_NAME_PATTERN =
            Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(kg|gr|g|ml|lt|l)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

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
        boolean shouldContinue = true;
        while (shouldContinue && page <= pageCount && page <= MAX_SEARCH_PAGES) {
            PageFetchResult pageFetchResult = fetchCatalogUrlPage(sourceUrl, page, pageCount, productsByExternalId);
            shouldContinue = pageFetchResult != null;
            if (shouldContinue) {
                pageCount = pageFetchResult.nextPageCount();
                shouldContinue = !pageFetchResult.shouldStop();
                page += 1;
            }
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
            if (!externalId.isBlank()) {
                String categoryName = extractCategoryName(entry);
                MarketplaceProductCandidate candidate = toCandidate(entry, quantityByExternalId);
                if (candidate != null) {
                    productsByExternalId.putIfAbsent(
                            externalId.toLowerCase(),
                            new CatalogUrlProductCandidate(categoryName, candidate)
                    );
                }
            }
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
        if (normalizeSearchText(keyword).isBlank()) {
            return List.of();
        }
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
                    MarketplaceProductCandidate candidate = toCandidate(entry, quantityByExternalId);
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
        Integer comboPackCount = extractComboPackCount(lower);
        if (comboPackCount != null && comboPackCount > 1) {
            return comboPackCount;
        }
        Integer listedPackCount = extractListedPackCount(lower);
        return listedPackCount != null && listedPackCount > 1 ? listedPackCount : null;
    }

    private Integer extractComboPackCount(String text) {
        for (String token : text.split("\\s+")) {
            Integer compact = parseCompactComboPackCount(token);
            if (compact != null) {
                return compact;
            }
        }
        int index = findNextDigit(text, 0);
        while (index >= 0) {
            Integer packCount = parseSpacedComboPackCount(text, index);
            if (packCount != null) {
                return packCount;
            }
            index = findNextDigit(text, index + 1);
        }
        return null;
    }

    private Integer extractListedPackCount(String text) {
        for (String token : text.split("\\s+")) {
            Integer compact = parseCompactListedPackCount(token);
            if (compact != null) {
                return compact;
            }
        }
        int index = findNextDigit(text, 0);
        while (index >= 0) {
            Integer packCount = parseSpacedListedPackCount(text, index);
            if (packCount != null) {
                return packCount;
            }
            index = findNextDigit(text, index + 1);
        }
        return null;
    }

    private Integer parseSpacedComboPackCount(String text, int startIndex) {
        int length = text.length();
        int current = startIndex;
        while (current < length && Character.isDigit(text.charAt(current))) {
            current++;
        }
        Integer packCount = parsePositiveInt(text.substring(startIndex, current));
        if (packCount == null) {
            return null;
        }

        int separatorIndex = skipWhitespace(text, current);
        if (separatorIndex >= length || Character.toLowerCase(text.charAt(separatorIndex)) != 'x') {
            return null;
        }

        int amountStart = skipWhitespace(text, separatorIndex + 1);
        int amountEnd = readDecimalEnd(text, amountStart);
        if (!isDecimalToken(text.substring(amountStart, amountEnd))) {
            return null;
        }

        int unitStart = skipWhitespace(text, amountEnd);
        int unitEnd = readLetterEnd(text, unitStart);
        if (unitEnd <= unitStart) {
            return null;
        }
        return isUnitToken(text.substring(unitStart, unitEnd)) ? packCount : null;
    }

    private Integer parseSpacedListedPackCount(String text, int startIndex) {
        int length = text.length();
        int current = startIndex;
        while (current < length && Character.isDigit(text.charAt(current))) {
            current++;
        }
        Integer packCount = parsePositiveInt(text.substring(startIndex, current));
        if (packCount == null) {
            return null;
        }

        int suffixStart = skipWhitespace(text, current);
        if (suffixStart < length && (text.charAt(suffixStart) == '\'' || text.charAt(suffixStart) == '’')) {
            suffixStart++;
        }
        int suffixEnd = readLetterEnd(text, suffixStart);
        if (suffixEnd <= suffixStart) {
            return null;
        }
        return isPackDescriptor(text.substring(suffixStart, suffixEnd)) ? packCount : null;
    }

    private int findNextDigit(String text, int startIndex) {
        int current = Math.max(0, startIndex);
        while (current < text.length() && !Character.isDigit(text.charAt(current))) {
            current++;
        }
        return current < text.length() ? current : -1;
    }

    private int readDecimalEnd(String text, int startIndex) {
        int current = startIndex;
        boolean hasDecimalSeparator = false;
        while (current < text.length()) {
            char currentChar = text.charAt(current);
            if (Character.isDigit(currentChar)) {
                current++;
                continue;
            }
            if (!hasDecimalSeparator && (currentChar == '.' || currentChar == ',')) {
                hasDecimalSeparator = true;
                current++;
                continue;
            }
            break;
        }
        return current;
    }

    private int readLetterEnd(String text, int startIndex) {
        int current = startIndex;
        while (current < text.length() && Character.isLetter(text.charAt(current))) {
            current++;
        }
        return current;
    }

    private Integer parseCompactComboPackCount(String token) {
        int separatorIndex = token.toLowerCase(Locale.ROOT).indexOf('x');
        if (separatorIndex <= 0 || separatorIndex >= token.length() - 2) {
            return null;
        }
        String packPart = token.substring(0, separatorIndex);
        if (!isPositiveIntegerToken(packPart)) {
            return null;
        }
        int unitStart = separatorIndex + 1;
        boolean hasDecimal = false;
        while (unitStart < token.length()) {
            char current = token.charAt(unitStart);
            if (Character.isDigit(current)) {
                unitStart++;
                continue;
            }
            if (!hasDecimal && (current == '.' || current == ',')) {
                hasDecimal = true;
                unitStart++;
                continue;
            }
            break;
        }
        if (unitStart >= token.length()) {
            return null;
        }
        if (!isDecimalToken(token.substring(separatorIndex + 1, unitStart))) {
            return null;
        }
        return isUnitToken(token.substring(unitStart)) ? parsePositiveInt(packPart) : null;
    }

    private Integer parseCompactListedPackCount(String token) {
        String normalized = token.replace("’", "").replace("'", "");
        for (String suffix : List.of("pack", "paket", "li", "lı", "lu", "lü")) {
            if (!normalized.toLowerCase(Locale.ROOT).endsWith(suffix)) {
                continue;
            }
            String packPart = normalized.substring(0, normalized.length() - suffix.length());
            if (isPositiveIntegerToken(packPart)) {
                return parsePositiveInt(packPart);
            }
        }
        return null;
    }

    private int skipWhitespace(String text, int index) {
        int current = index;
        while (current < text.length() && Character.isWhitespace(text.charAt(current))) {
            current++;
        }
        return current;
    }

    private boolean isPositiveIntegerToken(String token) {
        return parsePositiveInt(token) != null;
    }

    private boolean isDecimalToken(String token) {
        return parseLocalizedDecimal(token) != null;
    }

    private boolean isUnitToken(String token) {
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "kg", "gr", "g", "ml", "lt", "l" -> true;
            default -> false;
        };
    }

    private boolean isPackDescriptor(String token) {
        String normalized = token.toLowerCase(Locale.ROOT);
        return normalized.equals("pack")
                || normalized.equals("paket")
                || normalized.equals("li")
                || normalized.equals("lı")
                || normalized.equals("lu")
                || normalized.equals("lü");
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
