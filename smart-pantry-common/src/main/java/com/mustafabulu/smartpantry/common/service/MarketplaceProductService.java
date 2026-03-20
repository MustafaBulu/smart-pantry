package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.common.dto.response.BulkAddResponse;
import com.mustafabulu.smartpantry.common.dto.response.BulkAddResultItem;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.core.util.MarketplacePricingMetadataUpdater;
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
    private MarketplaceCatalogUrlFetchResolverService catalogUrlFetchResolverService;
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

    @Autowired(required = false)
    public void setCatalogUrlFetchResolverService(
            MarketplaceCatalogUrlFetchResolverService catalogUrlFetchResolverService
    ) {
        this.catalogUrlFetchResolverService = catalogUrlFetchResolverService;
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

    @Transactional
    public void syncCatalogForMarketplace(Marketplace marketplace) {
        if (marketplace == null) {
            return;
        }
        MarketplaceProductConnector connector = connectorRegistry.get(marketplace).orElse(null);
        SyncStats totals = new SyncStats(0, 0, 0, 0);

        List<Category> categories = categoryRepository.findAll().stream()
                .filter(category -> category.getName() != null && !category.getName().isBlank())
                .toList();
        for (Category category : categories) {
            totals = totals.plus(syncCatalogForCategory(marketplace, category, connector));
        }
        log.info(
                "catalog sync completed: marketplace={}, categoryCount={}, candidateCount={}, createdCount={}, updatedCount={}",
                marketplace.getCode(),
                totals.categoryCount(),
                totals.candidateCount(),
                totals.createdCount(),
                totals.updatedCount()
        );
    }

    @Transactional
    public CatalogSyncResult syncCatalogFromUrls(Marketplace marketplace, List<String> sourceUrls) {
        if (marketplace == null || sourceUrls == null || sourceUrls.isEmpty()) {
            return new CatalogSyncResult("", 0, 0, 0, 0);
        }
        if (catalogUrlFetchResolverService == null) {
            return new CatalogSyncResult(marketplace.getCode(), 0, 0, 0, 0);
        }
        MarketplaceProductConnector connector = connectorRegistry.get(marketplace).orElse(null);
        Map<String, Category> categoryByName = new LinkedHashMap<>();
        Map<String, Map<String, MarketplaceProduct>> existingByCategoryKey = new LinkedHashMap<>();
        SyncStats totals = new SyncStats(0, 0, 0, 0);
        for (String sourceUrl : sourceUrls) {
            totals = totals.plus(syncCatalogForUrl(
                    marketplace,
                    sourceUrl,
                    connector,
                    categoryByName,
                    existingByCategoryKey
            ));
        }
        log.info(
                "catalog sync from URLs completed: marketplace={}, sourceUrlCount={}, categoryCount={}, candidateCount={}, createdCount={}, updatedCount={}",
                marketplace.getCode(),
                sourceUrls.size(),
                totals.categoryCount(),
                totals.candidateCount(),
                totals.createdCount(),
                totals.updatedCount()
        );
        return new CatalogSyncResult(
                marketplace.getCode(),
                totals.categoryCount(),
                totals.candidateCount(),
                totals.createdCount(),
                totals.updatedCount()
        );
    }

    private SyncStats syncCatalogForCategory(
            Marketplace marketplace,
            Category category,
            MarketplaceProductConnector connector
    ) {
        Map<String, MarketplaceMetadata> fetched = fetchMetadataByExternalId(marketplace, category.getName());
        if (fetched.isEmpty()) {
            return new SyncStats(1, 0, 0, 0);
        }
        Map<String, MarketplaceProduct> existingByExternalId = loadExistingByExternalId(marketplace, category);
        List<MarketplaceProduct> toSave = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;
        for (Map.Entry<String, MarketplaceMetadata> entry : fetched.entrySet()) {
            String externalId = normalizeExternalId(entry.getKey());
            if (externalId != null) {
                PreparedMarketplaceProduct prepared = prepareMarketplaceProduct(
                        marketplace,
                        category,
                        externalId,
                        connector,
                        existingByExternalId
                );
                MarketplaceProduct marketplaceProduct = prepared.product();
                boolean created = prepared.created();
                boolean updated = applyCatalogMetadata(marketplaceProduct, entry.getValue());
                if (!created && !updated) {
                    continue;
                }
                toSave.add(marketplaceProduct);
                if (created) {
                    createdCount += 1;
                } else {
                    updatedCount += 1;
                }
            }
        }
        if (!toSave.isEmpty()) {
            marketplaceProductRepository.saveAll(toSave);
        }
        return new SyncStats(1, fetched.size(), createdCount, updatedCount);
    }

    private SyncStats syncCatalogForUrl(
            Marketplace marketplace,
            String sourceUrl,
            MarketplaceProductConnector connector,
            Map<String, Category> categoryByName,
            Map<String, Map<String, MarketplaceProduct>> existingByCategoryKey
    ) {
        List<MarketplaceCatalogUrlFetchService.CatalogUrlProductCandidate> fetched =
                catalogUrlFetchResolverService.fetchAllByUrl(marketplace, sourceUrl);
        int categoryCount = 0;
        int createdCount = 0;
        int updatedCount = 0;
        for (MarketplaceCatalogUrlFetchService.CatalogUrlProductCandidate fetchedCandidate : fetched) {
            UrlCandidateContext context = resolveUrlCandidateContext(
                    marketplace,
                    fetchedCandidate,
                    categoryByName,
                    existingByCategoryKey
            );
            if (context == null) {
                continue;
            }
            if (context.newCategory()) {
                categoryCount += 1;
            }
            SyncStats persisted = persistUrlCandidate(context, connector);
            createdCount += persisted.createdCount();
            updatedCount += persisted.updatedCount();
        }
        return new SyncStats(categoryCount, fetched.size(), createdCount, updatedCount);
    }

    private UrlCandidateContext resolveUrlCandidateContext(
            Marketplace marketplace,
            MarketplaceCatalogUrlFetchService.CatalogUrlProductCandidate fetchedCandidate,
            Map<String, Category> categoryByName,
            Map<String, Map<String, MarketplaceProduct>> existingByCategoryKey
    ) {
        MarketplaceCategoryFetchService.MarketplaceProductCandidate candidate = fetchedCandidate.candidate();
        if (candidate == null || candidate.externalId() == null || candidate.externalId().isBlank()) {
            return null;
        }
        String normalizedCategoryName = normalizeUrlCategoryName(fetchedCandidate.categoryName());
        String normalizedCategoryKey = normalizeCategoryName(normalizedCategoryName);
        boolean newCategory = !categoryByName.containsKey(normalizedCategoryKey);
        Category category = categoryByName.computeIfAbsent(normalizedCategoryKey, key ->
                categoryRepository.findByNameIgnoreCase(normalizedCategoryName)
                        .orElseGet(() -> {
                            Category createdCategory = new Category();
                            createdCategory.setName(normalizedCategoryName);
                            return categoryRepository.save(createdCategory);
                        })
        );
        Map<String, MarketplaceProduct> existingByExternalId =
                existingByCategoryKey.computeIfAbsent(
                        normalizedCategoryKey,
                        key -> loadExistingByExternalId(marketplace, category)
                );
        String externalId = normalizeExternalId(candidate.externalId());
        if (externalId == null) {
            return null;
        }
        return new UrlCandidateContext(marketplace, category, existingByExternalId, candidate, externalId, newCategory);
    }

    private SyncStats persistUrlCandidate(
            UrlCandidateContext context,
            MarketplaceProductConnector connector
    ) {
        MarketplaceMetadata metadata = new MarketplaceMetadata(
                context.candidate().brandName(),
                context.candidate().imageUrl(),
                context.candidate().moneyPrice(),
                context.candidate().basketDiscountThreshold(),
                context.candidate().basketDiscountPrice(),
                context.candidate().campaignBuyQuantity(),
                context.candidate().campaignPayQuantity(),
                context.candidate().effectivePrice()
        );
        PreparedMarketplaceProduct prepared = prepareMarketplaceProduct(
                context.marketplace(),
                context.category(),
                context.externalId(),
                connector,
                context.existingByExternalId()
        );
        MarketplaceProduct marketplaceProduct = prepared.product();
        boolean created = prepared.created();
        boolean updated = applyCatalogMetadata(marketplaceProduct, metadata);
        if (!created && !updated) {
            return new SyncStats(0, 0, 0, 0);
        }
        MarketplaceProduct saved = marketplaceProductRepository.save(marketplaceProduct);
        context.existingByExternalId().put(normalizeCategoryName(context.externalId()), saved);
        return created
                ? new SyncStats(0, 0, 1, 0)
                : new SyncStats(0, 0, 0, 1);
    }

    private String normalizeUrlCategoryName(String rawCategoryName) {
        if (rawCategoryName == null || rawCategoryName.isBlank()) {
            return "Genel";
        }
        return rawCategoryName.trim();
    }

    private String normalizeExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return null;
        }
        return externalId.trim();
    }

    private String normalizeCategoryName(String value) {
        return value.trim().toLowerCase();
    }

    private Map<String, MarketplaceProduct> loadExistingByExternalId(
            Marketplace marketplace,
            Category category
    ) {
        Map<String, MarketplaceProduct> existingByExternalId = new LinkedHashMap<>();
        for (MarketplaceProduct existing : marketplaceProductRepository.findByMarketplaceAndCategory(marketplace, category)) {
            String normalizedExternalId = normalizeExternalId(existing.getExternalId());
            if (normalizedExternalId == null) {
                continue;
            }
            existingByExternalId.put(normalizeCategoryName(normalizedExternalId), existing);
        }
        return existingByExternalId;
    }

    private PreparedMarketplaceProduct prepareMarketplaceProduct(
            Marketplace marketplace,
            Category category,
            String externalId,
            MarketplaceProductConnector connector,
            Map<String, MarketplaceProduct> existingByExternalId
    ) {
        String normalizedExternalId = normalizeCategoryName(externalId);
        MarketplaceProduct marketplaceProduct = existingByExternalId.get(normalizedExternalId);
        boolean created = false;
        if (marketplaceProduct == null) {
            marketplaceProduct = new MarketplaceProduct();
            marketplaceProduct.setMarketplace(marketplace);
            marketplaceProduct.setCategory(category);
            marketplaceProduct.setExternalId(externalId);
            created = true;
        }
        if (isBlank(marketplaceProduct.getProductUrl()) && connector != null) {
            marketplaceProduct.setProductUrl(connector.buildProductUrl(externalId));
        }
        return new PreparedMarketplaceProduct(marketplaceProduct, created);
    }

    private boolean applyCatalogMetadata(MarketplaceProduct product, MarketplaceMetadata metadata) {
        boolean updated = false;
        if (metadata == null) {
            return false;
        }
        if (!isBlank(metadata.brandName()) && !metadata.brandName().equals(product.getBrandName())) {
            product.setBrandName(metadata.brandName());
            updated = true;
        }
        if (!isBlank(metadata.imageUrl()) && !metadata.imageUrl().equals(product.getImageUrl())) {
            product.setImageUrl(metadata.imageUrl());
            updated = true;
        }
        if (metadata.moneyPrice() != null && !metadata.moneyPrice().equals(product.getMoneyPrice())) {
            product.setMoneyPrice(metadata.moneyPrice());
            updated = true;
        }
        updated |= MarketplacePricingMetadataUpdater.applyCampaignAndDiscountMetadata(
                product,
                metadata.basketDiscountThreshold(),
                metadata.basketDiscountPrice(),
                metadata.campaignBuyQuantity(),
                metadata.campaignPayQuantity(),
                metadata.effectivePrice()
        );
        return updated;
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

    private record SyncStats(
            int categoryCount,
            int candidateCount,
            int createdCount,
            int updatedCount
    ) {
        private SyncStats plus(SyncStats other) {
            return new SyncStats(
                    categoryCount + other.categoryCount,
                    candidateCount + other.candidateCount,
                    createdCount + other.createdCount,
                    updatedCount + other.updatedCount
            );
        }
    }

    private record UrlCandidateContext(
            Marketplace marketplace,
            Category category,
            Map<String, MarketplaceProduct> existingByExternalId,
            MarketplaceCategoryFetchService.MarketplaceProductCandidate candidate,
            String externalId,
            boolean newCategory
    ) {
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

    private record PreparedMarketplaceProduct(
            MarketplaceProduct product,
            boolean created
    ) {
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

    public record CatalogSyncResult(
            String marketplaceCode,
            int categoryCount,
            int candidateCount,
            int createdCount,
            int updatedCount
    ) {
    }
}
