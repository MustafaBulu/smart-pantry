package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.common.dto.response.BulkAddResponse;
import com.mustafabulu.smartpantry.common.dto.response.BulkAddResultItem;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.common.repository.CategoryRepository;
import com.mustafabulu.smartpantry.common.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.common.repository.PriceHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class MarketplaceProductService {

    private static final Duration METADATA_CACHE_TTL = Duration.ofMinutes(5);

    private final CategoryRepository categoryRepository;
    private final MarketplaceProductRepository marketplaceProductRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MarketplaceProductConnectorRegistry connectorRegistry;
    private final MarketplaceCategorySearchService marketplaceCategorySearchService;
    private final TaskExecutor taskExecutor;
    private final Map<MetadataCacheKey, MetadataCacheEntry> metadataCache = new ConcurrentHashMap<>();

    @Autowired
    public MarketplaceProductService(
            CategoryRepository categoryRepository,
            MarketplaceProductRepository marketplaceProductRepository,
            PriceHistoryRepository priceHistoryRepository,
            MarketplaceProductConnectorRegistry connectorRegistry,
            MarketplaceCategorySearchService marketplaceCategorySearchService,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.categoryRepository = categoryRepository;
        this.marketplaceProductRepository = marketplaceProductRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.connectorRegistry = connectorRegistry;
        this.marketplaceCategorySearchService = marketplaceCategorySearchService;
        this.taskExecutor = taskExecutor;
    }

    public AddProductResult addProduct(String marketplaceCode, String categoryName, String productId) {
        return addProduct(marketplaceCode, categoryName, productId, null);
    }

    private AddProductResult addProduct(
            String marketplaceCode,
            String categoryName,
            String productId,
            Map<String, MarketplaceMetadata> metadataByExternalId
    ) {
        long startedAt = System.nanoTime();
        HttpStatus resultStatus = HttpStatus.BAD_REQUEST;
        try {
            Marketplace marketplace = Marketplace.fromCode(marketplaceCode);
            if (marketplace == null) {
                log.warn("addProduct rejected: unknown marketplaceCode={}", marketplaceCode);
                return AddProductResult.badRequest(ResponseMessages.INVALID_MARKETPLACE_CODE);
            }
            MarketplaceProductConnector connector = connectorRegistry.get(marketplace).orElse(null);
            if (connector == null) {
                log.warn("addProduct rejected: no connector for marketplace={}", marketplace.getCode());
                return AddProductResult.badRequest(ResponseMessages.INVALID_MARKETPLACE_CODE);
            }

            Category category = categoryRepository.findByNameIgnoreCase(categoryName.trim())
                    .orElse(null);
            if (category == null) {
                return AddProductResult.notFound();
            }

            String externalId = productId.trim();
            String productUrl = connector.buildProductUrl(externalId);

            MarketplaceProduct marketplaceProduct = marketplaceProductRepository
                    .findByMarketplaceAndCategoryAndExternalId(marketplace, category, externalId)
                    .orElse(null);
            boolean createdNew = false;
            if (marketplaceProduct == null) {
                MarketplaceProduct created = new MarketplaceProduct();
                created.setMarketplace(marketplace);
                created.setCategory(category);
                created.setExternalId(externalId);
                created.setProductUrl(productUrl);
                applyMetadata(created, metadataByExternalId, marketplace, category.getName(), externalId);
                marketplaceProduct = marketplaceProductRepository.save(created);
                createdNew = true;
            } else {
                applyMetadataIfMissing(
                        marketplaceProduct,
                        metadataByExternalId,
                        marketplace,
                        category.getName(),
                        externalId
                );
                marketplaceProductRepository.save(marketplaceProduct);
            }

            if (createdNew) {
                boolean verified = verifyNewProduct(connector, category, marketplaceProduct);
                if (!verified) {
                    boolean metadataMatchedCandidate = hasMarketplaceCandidateMetadata(
                            metadataByExternalId,
                            marketplace,
                            categoryName,
                            externalId
                    );
                    if (!metadataMatchedCandidate) {
                        marketplaceProductRepository.delete(marketplaceProduct);
                        return AddProductResult.badRequest(ResponseMessages.PRODUCT_COULD_NOT_BE_ADDED);
                    }
                    log.warn(
                            "new product verification skipped due to marketplace candidate metadata: marketplace={}, categoryName={}, externalId={}",
                            marketplace.getCode(),
                            categoryName,
                            externalId
                    );
                    triggerDetailRefreshAsync(connector, category, marketplaceProduct);
                }
            } else {
                triggerDetailRefreshAsync(connector, category, marketplaceProduct);
            }

            resultStatus = createdNew ? HttpStatus.CREATED : HttpStatus.OK;
            return AddProductResult.status(resultStatus);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(
                    "addProduct timing: marketplaceCode={}, categoryName={}, productId={}, status={}, durationMs={}",
                    marketplaceCode,
                    categoryName,
                    productId,
                    resultStatus.value(),
                    durationMs
            );
        }
    }

    private boolean hasMarketplaceCandidateMetadata(
            Map<String, MarketplaceMetadata> metadataByExternalId,
            Marketplace marketplace,
            String categoryName,
            String externalId
    ) {
        if (externalId == null || externalId.isBlank()) {
            return false;
        }
        if (metadataByExternalId != null) {
            return metadataByExternalId.containsKey(externalId);
        }
        Map<String, MarketplaceMetadata> fetched = fetchMetadataByExternalId(marketplace, categoryName);
        return fetched.containsKey(externalId);
    }

    public BulkAddResponse addProducts(String marketplaceCode, String categoryName, List<String> productIds) {
        Marketplace marketplace = Marketplace.fromCode(marketplaceCode);
        Map<String, MarketplaceMetadata> metadataByExternalId = fetchMetadataByExternalId(
                marketplace,
                categoryName
        );
        List<BulkAddResultItem> results = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int failed = 0;
        for (String productId : productIds) {
            if (productId == null || productId.isBlank()) {
                failed++;
                results.add(new BulkAddResultItem(
                        productId,
                        HttpStatus.BAD_REQUEST.value(),
                        ResponseMessages.PRODUCT_COULD_NOT_BE_ADDED
                ));
                continue;
            }
            AddProductResult result = addProduct(
                    marketplaceCode,
                    categoryName,
                    productId,
                    metadataByExternalId
            );
            if (result.status().is2xxSuccessful()) {
                if (HttpStatus.CREATED.equals(result.status())) {
                    created++;
                } else {
                    updated++;
                }
            } else {
                failed++;
            }
            results.add(new BulkAddResultItem(productId, result.status().value(), result.message()));
        }
        return new BulkAddResponse(productIds.size(), created, updated, failed, results);
    }

    private void applyMetadata(
            MarketplaceProduct marketplaceProduct,
            Map<String, MarketplaceMetadata> metadataByExternalId,
            Marketplace marketplace,
            String categoryName,
            String externalId
    ) {
        MarketplaceMetadata metadata = resolveMetadata(
                metadataByExternalId,
                marketplace,
                categoryName,
                externalId
        );
        marketplaceProduct.setBrandName(metadata.brandName());
        marketplaceProduct.setImageUrl(metadata.imageUrl());
        marketplaceProduct.setMoneyPrice(metadata.moneyPrice());
        marketplaceProduct.setBasketDiscountThreshold(metadata.basketDiscountThreshold());
        marketplaceProduct.setBasketDiscountPrice(metadata.basketDiscountPrice());
        marketplaceProduct.setCampaignBuyQuantity(metadata.campaignBuyQuantity());
        marketplaceProduct.setCampaignPayQuantity(metadata.campaignPayQuantity());
        marketplaceProduct.setEffectivePrice(metadata.effectivePrice());
    }

    private void applyMetadataIfMissing(
            MarketplaceProduct marketplaceProduct,
            Map<String, MarketplaceMetadata> metadataByExternalId,
            Marketplace marketplace,
            String categoryName,
            String externalId
    ) {
        MarketplaceMetadata metadata = resolveMetadata(
                metadataByExternalId,
                marketplace,
                categoryName,
                externalId
        );
        if (isBlank(marketplaceProduct.getBrandName())) {
            marketplaceProduct.setBrandName(metadata.brandName());
        }
        if (isBlank(marketplaceProduct.getImageUrl())) {
            marketplaceProduct.setImageUrl(metadata.imageUrl());
        }
        if (marketplaceProduct.getMoneyPrice() == null) {
            marketplaceProduct.setMoneyPrice(metadata.moneyPrice());
        }
        if (marketplaceProduct.getBasketDiscountThreshold() == null) {
            marketplaceProduct.setBasketDiscountThreshold(metadata.basketDiscountThreshold());
        }
        if (marketplaceProduct.getBasketDiscountPrice() == null) {
            marketplaceProduct.setBasketDiscountPrice(metadata.basketDiscountPrice());
        }
        if (marketplaceProduct.getCampaignBuyQuantity() == null) {
            marketplaceProduct.setCampaignBuyQuantity(metadata.campaignBuyQuantity());
        }
        if (marketplaceProduct.getCampaignPayQuantity() == null) {
            marketplaceProduct.setCampaignPayQuantity(metadata.campaignPayQuantity());
        }
        if (marketplaceProduct.getEffectivePrice() == null) {
            marketplaceProduct.setEffectivePrice(metadata.effectivePrice());
        }
    }

    private MarketplaceMetadata resolveMetadata(
            Map<String, MarketplaceMetadata> metadataByExternalId,
            Marketplace marketplace,
            String categoryName,
            String externalId
    ) {
        if (metadataByExternalId != null) {
            return metadataByExternalId.getOrDefault(externalId, MarketplaceMetadata.empty());
        }
        Map<String, MarketplaceMetadata> fetched = fetchMetadataByExternalId(marketplace, categoryName);
        return fetched.getOrDefault(externalId, MarketplaceMetadata.empty());
    }

    private Map<String, MarketplaceMetadata> fetchMetadataByExternalId(
            Marketplace marketplace,
            String categoryName
    ) {
        if (marketplaceCategorySearchService == null
                || marketplace == null
                || categoryName == null
                || categoryName.isBlank()) {
            return Map.of();
        }
        String normalizedCategory = categoryName.trim().toLowerCase();
        MetadataCacheKey cacheKey = new MetadataCacheKey(marketplace, normalizedCategory);
        MetadataCacheEntry cached = metadataCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.metadataByExternalId();
        }

        long startedAt = System.nanoTime();
        Map<String, MarketplaceMetadata> metadataByExternalId = new LinkedHashMap<>();
        List<MarketplaceCategoryFetchService.MarketplaceProductCandidate> candidates =
                marketplaceCategorySearchService.fetchByMarketplace(categoryName, marketplace);
        for (MarketplaceCategoryFetchService.MarketplaceProductCandidate candidate : candidates) {
            metadataByExternalId.putIfAbsent(
                    candidate.externalId(),
                    new MarketplaceMetadata(
                            candidate.brandName(),
                            candidate.imageUrl(),
                            candidate.moneyPrice(),
                            candidate.basketDiscountThreshold(),
                            candidate.basketDiscountPrice(),
                            candidate.campaignBuyQuantity(),
                            candidate.campaignPayQuantity(),
                            candidate.effectivePrice()
                    )
            );
        }
        metadataCache.put(cacheKey, new MetadataCacheEntry(metadataByExternalId, Instant.now()));
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "metadata fetch timing: marketplace={}, categoryName={}, count={}, durationMs={}",
                marketplace.getCode(),
                categoryName,
                metadataByExternalId.size(),
                durationMs
        );
        return metadataByExternalId;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void triggerDetailRefreshAsync(
            MarketplaceProductConnector connector,
            Category category,
            MarketplaceProduct marketplaceProduct
    ) {
        try {
            taskExecutor.execute(() -> {
                try {
                    boolean recorded = connector.recordDetailsForProduct(category, marketplaceProduct);
                    if (!recorded) {
                        log.warn(
                                "async detail refresh failed: marketplace={}, externalId={}, category={}",
                                marketplaceProduct.getMarketplace().getCode(),
                                marketplaceProduct.getExternalId(),
                                category.getName()
                        );
                    }
                } catch (SPException ex) {
                    log.warn(
                            "async detail refresh exception: marketplace={}, externalId={}, status={}",
                            marketplaceProduct.getMarketplace().getCode(),
                            marketplaceProduct.getExternalId(),
                            ex.getStatusCode(),
                            ex
                    );
                } catch (Exception ex) {
                    log.warn(
                            "async detail refresh unexpected error: marketplace={}, externalId={}",
                            marketplaceProduct.getMarketplace().getCode(),
                            marketplaceProduct.getExternalId(),
                            ex
                    );
                }
            });
        } catch (Exception ex) {
            log.warn(
                    "async detail refresh could not be scheduled: marketplace={}, externalId={}",
                    marketplaceProduct.getMarketplace().getCode(),
                    marketplaceProduct.getExternalId(),
                    ex
            );
        }
    }

    private boolean verifyNewProduct(
            MarketplaceProductConnector connector,
            Category category,
            MarketplaceProduct marketplaceProduct
    ) {
        try {
            return connector.recordDetailsForProduct(category, marketplaceProduct);
        } catch (SPException ex) {
            log.warn(
                    "new product verification failed: marketplace={}, externalId={}, status={}",
                    marketplaceProduct.getMarketplace().getCode(),
                    marketplaceProduct.getExternalId(),
                    ex.getStatusCode(),
                    ex
            );
            return false;
        } catch (Exception ex) {
            log.warn(
                    "new product verification unexpected error: marketplace={}, externalId={}",
                    marketplaceProduct.getMarketplace().getCode(),
                    marketplaceProduct.getExternalId(),
                    ex
            );
            return false;
        }
    }

    @Transactional
    public DeleteMarketplaceProductResult deleteMarketplaceProduct(
            String marketplaceCode,
            String externalId,
            String categoryName
    ) {
        Marketplace marketplace = Marketplace.fromCode(marketplaceCode);
        if (marketplace == null) {
            return DeleteMarketplaceProductResult.badRequest(ResponseMessages.INVALID_MARKETPLACE_CODE);
        }
        if (externalId == null || externalId.isBlank()) {
            return DeleteMarketplaceProductResult.badRequest(ResponseMessages.MARKETPLACE_PRODUCT_NOT_FOUND);
        }
        List<MarketplaceProduct> candidates = marketplaceProductRepository.findByMarketplaceAndExternalId(
                marketplace,
                externalId.trim()
        );
        if (categoryName != null && !categoryName.isBlank()) {
            Category category = categoryRepository.findByNameIgnoreCase(categoryName.trim())
                    .orElse(null);
            if (category == null) {
                return DeleteMarketplaceProductResult.notFound(ResponseMessages.CATEGORY_NOT_FOUND);
            }
            candidates = candidates.stream()
                    .filter(product -> category.equals(product.getCategory()))
                    .toList();
        }
        if (candidates.isEmpty()) {
            return DeleteMarketplaceProductResult.notFound(ResponseMessages.MARKETPLACE_PRODUCT_NOT_FOUND);
        }
        for (MarketplaceProduct marketplaceProduct : candidates) {
            priceHistoryRepository.deleteByMarketplaceProductId(marketplaceProduct.getId());
            marketplaceProductRepository.delete(marketplaceProduct);
        }
        return DeleteMarketplaceProductResult.ok(candidates.size());
    }

    public RefreshProductResult refreshProduct(
            String marketplaceCode,
            String externalId,
            String categoryName
    ) {
        Marketplace marketplace = Marketplace.fromCode(marketplaceCode);
        if (marketplace == null) {
            return RefreshProductResult.badRequest(ResponseMessages.INVALID_MARKETPLACE_CODE);
        }
        MarketplaceProductConnector connector = connectorRegistry.get(marketplace).orElse(null);
        if (connector == null) {
            return RefreshProductResult.badRequest(ResponseMessages.INVALID_MARKETPLACE_CODE);
        }
        if (externalId == null || externalId.isBlank()) {
            return RefreshProductResult.badRequest(ResponseMessages.MARKETPLACE_PRODUCT_NOT_FOUND);
        }
        List<MarketplaceProduct> candidates = marketplaceProductRepository.findByMarketplaceAndExternalId(
                marketplace,
                externalId.trim()
        );
        if (categoryName != null && !categoryName.isBlank()) {
            Category category = categoryRepository.findByNameIgnoreCase(categoryName.trim())
                    .orElse(null);
            if (category == null) {
                return RefreshProductResult.notFound(ResponseMessages.CATEGORY_NOT_FOUND);
            }
            candidates = candidates.stream()
                    .filter(product -> category.equals(product.getCategory()))
                    .toList();
        }
        if (candidates.isEmpty()) {
            return RefreshProductResult.notFound(ResponseMessages.MARKETPLACE_PRODUCT_NOT_FOUND);
        }
        if (candidates.size() > 1) {
            return RefreshProductResult.conflict();
        }
        MarketplaceProduct marketplaceProduct = candidates.getFirst();
        Category category = marketplaceProduct.getCategory();
        boolean recorded = connector.recordDetailsForProduct(category, marketplaceProduct);
        if (!recorded) {
            return RefreshProductResult.badRequest(ResponseMessages.MARKETPLACE_REFRESH_FAILED);
        }
        return RefreshProductResult.ok();
    }

    private record MetadataCacheKey(Marketplace marketplace, String normalizedCategoryName) {
    }

    private record MetadataCacheEntry(
            Map<String, MarketplaceMetadata> metadataByExternalId,
            Instant loadedAt
    ) {
        boolean isExpired() {
            return loadedAt.plus(METADATA_CACHE_TTL).isBefore(Instant.now());
        }
    }

    private record MarketplaceMetadata(
            String brandName,
            String imageUrl,
            java.math.BigDecimal moneyPrice,
            java.math.BigDecimal basketDiscountThreshold,
            java.math.BigDecimal basketDiscountPrice,
            Integer campaignBuyQuantity,
            Integer campaignPayQuantity,
            java.math.BigDecimal effectivePrice
    ) {
        static MarketplaceMetadata empty() {
            return new MarketplaceMetadata("", "", null, null, null, null, null, null);
        }
    }

    public record AddProductResult(HttpStatus status, String message) {
        static AddProductResult badRequest(String message) {
            return new AddProductResult(HttpStatus.BAD_REQUEST, message);
        }

        static AddProductResult notFound() {
            return new AddProductResult(HttpStatus.NOT_FOUND, ResponseMessages.CATEGORY_NOT_FOUND);
        }

        static AddProductResult status(HttpStatus status) {
            return new AddProductResult(status, ResponseMessages.PRODUCT_ADDED);
        }
    }

    public record DeleteMarketplaceProductResult(HttpStatus status, String message, int deletedCount) {
        static DeleteMarketplaceProductResult badRequest(String message) {
            return new DeleteMarketplaceProductResult(HttpStatus.BAD_REQUEST, message, 0);
        }

        static DeleteMarketplaceProductResult notFound(String message) {
            return new DeleteMarketplaceProductResult(HttpStatus.NOT_FOUND, message, 0);
        }

        static DeleteMarketplaceProductResult ok(int count) {
            return new DeleteMarketplaceProductResult(
                    HttpStatus.OK,
                    ResponseMessages.MARKETPLACE_PRODUCT_REMOVED,
                    count
            );
        }
    }

    public record RefreshProductResult(HttpStatus status, String message) {
        static RefreshProductResult badRequest(String message) {
            return new RefreshProductResult(HttpStatus.BAD_REQUEST, message);
        }

        static RefreshProductResult notFound(String message) {
            return new RefreshProductResult(HttpStatus.NOT_FOUND, message);
        }

        static RefreshProductResult conflict() {
            return new RefreshProductResult(HttpStatus.CONFLICT, ResponseMessages.MARKETPLACE_PRODUCT_AMBIGUOUS);
        }

        static RefreshProductResult ok() {
            return new RefreshProductResult(HttpStatus.OK, ResponseMessages.MARKETPLACE_REFRESHED);
        }
    }
}
