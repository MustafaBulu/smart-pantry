package com.mustafabulu.smartpantry.migros.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.service.MarketplaceCatalogCategoryRangeImportService;
import com.mustafabulu.smartpantry.migros.model.MigrosCatalogProduct;
import com.mustafabulu.smartpantry.migros.repository.MigrosCatalogProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "marketplace.mg", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigrosCatalogCategoryRangeImportService implements MarketplaceCatalogCategoryRangeImportService {

    private static final int MAX_PAGE_LIMIT = 500;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final MigrosCatalogProductRepository migrosCatalogProductRepository;

    @Override
    public Marketplace marketplace() {
        return Marketplace.MG;
    }

    @Override
    @Transactional
    public CatalogCategoryRangeImportResult importFromCategoryRange(
            String sourceUrl,
            int startCategoryId,
            int endCategoryId
    ) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return emptyImportResult();
        }
        ImportCollection collection = collectFromCategoryRange(sourceUrl, startCategoryId, endCategoryId);
        PersistenceStats persistenceStats = persistRawProducts(collection.byExternalId());

        log.info(
                "migros category import completed: categoryRange={}..{}, totalPageCount={}, totalCollectedProductCount={}, uniqueProductCount={}, createdCount={}, updatedCount={}",
                startCategoryId,
                endCategoryId,
                collection.totalPageCount(),
                collection.totalCollectedProductCount(),
                collection.byExternalId().size(),
                persistenceStats.createdCount(),
                persistenceStats.updatedCount()
        );
        return new CatalogCategoryRangeImportResult(
                Marketplace.MG.getCode(),
                Math.max(0, endCategoryId - startCategoryId + 1),
                collection.totalPageCount(),
                collection.totalCollectedProductCount(),
                collection.byExternalId().size(),
                persistenceStats.createdCount(),
                persistenceStats.updatedCount()
        );
    }

    private CatalogCategoryRangeImportResult emptyImportResult() {
        return new CatalogCategoryRangeImportResult(Marketplace.MG.getCode(), 0, 0, 0, 0, 0, 0);
    }

    private ImportCollection collectFromCategoryRange(String sourceUrl, int startCategoryId, int endCategoryId) {
        Map<String, RawCatalogProduct> byExternalId = new LinkedHashMap<>();
        int totalPageCount = 0;
        int totalCollectedProductCount = 0;

        for (int categoryId = startCategoryId; categoryId <= endCategoryId; categoryId += 1) {
            CategoryCollectStats categoryStats = collectCategory(sourceUrl, categoryId, byExternalId);
            totalPageCount += categoryStats.totalPageCount();
            totalCollectedProductCount += categoryStats.totalCollectedProductCount();
        }

        return new ImportCollection(byExternalId, totalPageCount, totalCollectedProductCount);
    }

    private CategoryCollectStats collectCategory(
            String sourceUrl,
            int categoryId,
            Map<String, RawCatalogProduct> byExternalId
    ) {
        int totalPageCount = 0;
        int totalCollectedProductCount = 0;
        int page = 1;
        int pageCount = 1;

        boolean shouldContinue = true;
        while (shouldContinue && page <= pageCount && page <= MAX_PAGE_LIMIT) {
            PageCollectResult pageResult = collectPage(sourceUrl, categoryId, page, pageCount, byExternalId);
            shouldContinue = pageResult != null;
            if (shouldContinue) {
                pageCount = pageResult.nextPageCount();
                totalPageCount += 1;
                totalCollectedProductCount += pageResult.pageItemCount();
                shouldContinue = !pageResult.shouldStop();
                page += 1;
            }
        }

        return new CategoryCollectStats(totalPageCount, totalCollectedProductCount);
    }

    private PageCollectResult collectPage(
            String sourceUrl,
            int categoryId,
            int page,
            int pageCount,
            Map<String, RawCatalogProduct> byExternalId
    ) {
        String pagedUrl = withQueryParams(sourceUrl, categoryId, page);
        if (pagedUrl.isBlank()) {
            return null;
        }

        Request request = buildCatalogRequest(pagedUrl);
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }

            JsonNode data = readDataNode(response);
            if (data == null) {
                return null;
            }

            int resolvedPageCount = Math.max(pageCount, data.path("pageCount").asInt(pageCount));
            JsonNode entries = data.path("storeProductInfos");
            int pageItemCount = collectPageProducts(entries, categoryId, byExternalId);

            log.info(
                    "migros category import progress: categoryId={}, page={}/{}, pageItemCount={}, uniqueProductCount={}",
                    categoryId,
                    page,
                    resolvedPageCount,
                    pageItemCount,
                    byExternalId.size()
            );
            boolean shouldStop = !entries.isArray() || entries.isEmpty();
            return new PageCollectResult(resolvedPageCount, pageItemCount, shouldStop);
        } catch (IOException ex) {
            log.warn("migros category import request failed: categoryId={}, page={}", categoryId, page, ex);
            return null;
        }
    }

    private Request buildCatalogRequest(String pagedUrl) {
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

    private JsonNode readDataNode(Response response) throws IOException {
        JsonNode root = objectMapper.readTree(response.body().string());
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return null;
        }
        return data;
    }

    private int collectPageProducts(JsonNode entries, int categoryId, Map<String, RawCatalogProduct> byExternalId) {
        if (!entries.isArray()) {
            return 0;
        }
        int pageItemCount = 0;
        for (JsonNode entry : entries) {
            pageItemCount += 1;
            RawCatalogProduct product = toRawCatalogProduct(entry, categoryId);
            if (product == null || product.externalId().isBlank()) {
                continue;
            }
            byExternalId.putIfAbsent(product.externalId(), product);
        }
        return pageItemCount;
    }

    private PersistenceStats persistRawProducts(Map<String, RawCatalogProduct> byExternalId) {
        if (byExternalId.isEmpty()) {
            return new PersistenceStats(0, 0);
        }

        List<String> keys = new ArrayList<>(byExternalId.keySet());
        Map<String, MigrosCatalogProduct> existingByExternalId = new LinkedHashMap<>();
        for (MigrosCatalogProduct existing : migrosCatalogProductRepository.findByExternalIdIn(keys)) {
            existingByExternalId.put(existing.getExternalId(), existing);
        }

        List<MigrosCatalogProduct> toSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int createdCount = 0;
        int updatedCount = 0;

        for (RawCatalogProduct raw : byExternalId.values()) {
            MigrosCatalogProduct target = existingByExternalId.get(raw.externalId());
            boolean created = false;
            if (target == null) {
                target = new MigrosCatalogProduct();
                target.setExternalId(raw.externalId());
                created = true;
            }
            boolean changed = applyRawProduct(target, raw, now);
            if (created || changed) {
                toSave.add(target);
                if (created) {
                    createdCount += 1;
                } else {
                    updatedCount += 1;
                }
            }
        }

        if (!toSave.isEmpty()) {
            migrosCatalogProductRepository.saveAll(toSave);
        }
        return new PersistenceStats(createdCount, updatedCount);
    }

    private boolean applyRawProduct(
            MigrosCatalogProduct target,
            RawCatalogProduct raw,
            LocalDateTime now
    ) {
        boolean changed = false;
        changed |= updateIfDifferent(target.getProductName(), raw.productName(), target::setProductName);
        changed |= updateIfDifferent(target.getBrandName(), raw.brandName(), target::setBrandName);
        changed |= updateIfDifferent(target.getPrettyName(), raw.prettyName(), target::setPrettyName);
        changed |= updateIfDifferent(target.getCategoryId(), raw.categoryId(), target::setCategoryId);
        changed |= updateIfDifferent(target.getCategoryName(), raw.categoryName(), target::setCategoryName);
        changed |= updateIfDifferent(target.getImageUrl(), raw.imageUrl(), target::setImageUrl);
        changed |= updateIfDifferent(target.getStatus(), raw.status(), target::setStatus);
        changed |= updateIfDifferent(target.getUnit(), raw.unit(), target::setUnit);
        changed |= updateIfDifferent(target.getRegularPrice(), raw.regularPrice(), target::setRegularPrice);
        changed |= updateIfDifferent(target.getShownPrice(), raw.shownPrice(), target::setShownPrice);
        changed |= updateIfDifferent(target.getUnitPrice(), raw.unitPrice(), target::setUnitPrice);
        changed |= updateIfDifferent(target.getDiscountRate(), raw.discountRate(), target::setDiscountRate);
        if (target.getLastSeenAt() == null || !target.getLastSeenAt().equals(now)) {
            target.setLastSeenAt(now);
            changed = true;
        }
        return changed;
    }

    private <T> boolean updateIfDifferent(T current, T next, java.util.function.Consumer<T> setter) {
        if (current == null ? next == null : current.equals(next)) {
            return false;
        }
        setter.accept(next);
        return true;
    }

    private RawCatalogProduct toRawCatalogProduct(JsonNode entry, int fallbackCategoryId) {
        if (entry == null || entry.isNull()) {
            return null;
        }
        String externalId = entry.path("id").asText("").trim();
        if (externalId.isBlank()) {
            return null;
        }
        String productName = entry.path("name").asText("").trim();
        if (productName.isBlank()) {
            return null;
        }
        String brandName = entry.path("brand").path("name").asText("").trim();
        String prettyName = entry.path("prettyName").asText("").trim();
        int categoryId = entry.path("category").path("id").asInt(fallbackCategoryId);
        String categoryName = entry.path("category").path("name").asText("").trim();
        if (categoryName.isBlank()) {
            categoryName = entry.path("socialProofInfo").path("categoryName").asText("").trim();
        }
        String imageUrl = resolveImageUrl(entry.path("images"));
        String status = entry.path("status").asText("").trim();
        String unit = entry.path("unit").asText("").trim();
        BigDecimal regularPrice = parsePrice(entry.path("regularPrice"));
        BigDecimal shownPrice = parsePrice(entry.path("shownPrice"));
        String unitPrice = entry.path("unitPrice").asText("").trim();
        Integer discountRate = entry.path("discountRate").isNumber() ? entry.path("discountRate").asInt() : null;
        return new RawCatalogProduct(
                externalId,
                productName,
                brandName,
                prettyName,
                categoryId,
                categoryName,
                imageUrl,
                status,
                unit,
                regularPrice,
                shownPrice,
                unitPrice,
                discountRate
        );
    }

    private String resolveImageUrl(JsonNode images) {
        if (images == null || !images.isArray() || images.isEmpty()) {
            return "";
        }
        return images.get(0).path("urls").path("PRODUCT_LIST").asText("").trim();
    }

    private BigDecimal parsePrice(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            BigDecimal raw = BigDecimal.valueOf(node.asDouble());
            return raw.movePointLeft(2);
        }
        return null;
    }

    private String withQueryParams(String sourceUrl, int categoryId, int page) {
        HttpUrl parsed = HttpUrl.parse(sourceUrl);
        if (parsed == null) {
            return "";
        }
        return parsed.newBuilder()
                .setQueryParameter("category-id", String.valueOf(categoryId))
                .setQueryParameter("sayfa", String.valueOf(Math.max(1, page)))
                .build()
                .toString();
    }

    private record RawCatalogProduct(
            String externalId,
            String productName,
            String brandName,
            String prettyName,
            Integer categoryId,
            String categoryName,
            String imageUrl,
            String status,
            String unit,
            BigDecimal regularPrice,
            BigDecimal shownPrice,
            String unitPrice,
            Integer discountRate
    ) {
    }

    private record PageCollectResult(
            int nextPageCount,
            int pageItemCount,
            boolean shouldStop
    ) {
    }

    private record CategoryCollectStats(
            int totalPageCount,
            int totalCollectedProductCount
    ) {
    }

    private record ImportCollection(
            Map<String, RawCatalogProduct> byExternalId,
            int totalPageCount,
            int totalCollectedProductCount
    ) {
    }

    private record PersistenceStats(
            int createdCount,
            int updatedCount
    ) {
    }
}
