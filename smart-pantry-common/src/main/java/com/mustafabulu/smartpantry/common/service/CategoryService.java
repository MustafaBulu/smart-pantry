package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.dto.response.CategoryResponse;
import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductAddedResponse;
import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductCandidateResponse;
import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductEntryResponse;
import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductMatchPairResponse;
import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductMatchScoreResponse;
import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.common.core.util.MarketplacePriceNormalizer;
import com.mustafabulu.smartpantry.common.core.util.MarketplaceCodeResolver;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.common.model.MarketplaceManualMatch;
import com.mustafabulu.smartpantry.common.model.PriceHistory;
import com.mustafabulu.smartpantry.common.model.Product;
import com.mustafabulu.smartpantry.common.repository.CategoryRepository;
import com.mustafabulu.smartpantry.common.repository.MarketplaceManualMatchRepository;
import com.mustafabulu.smartpantry.common.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.common.repository.ProductRepository;
import com.mustafabulu.smartpantry.common.repository.PriceHistoryRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
@Slf4j
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MarketplaceProductRepository marketplaceProductRepository;
    private final MarketplaceManualMatchRepository marketplaceManualMatchRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MarketplaceCategorySearchService marketplaceCategorySearchService;
    private final CrossPlatformProductMatcherService crossPlatformProductMatcherService;

    public CategoryResponse createCategory(String name, String mainCategory) {
        if (name == null || name.isBlank()) {
            throw new SPException(
                    HttpStatus.BAD_REQUEST,
                    ResponseMessages.CATEGORY_NAME_REQUIRED,
                    ResponseMessages.CATEGORY_NAME_REQUIRED_CODE
            );
        }
        String trimmedName = name.trim();
        if (categoryRepository.findByNameIgnoreCase(trimmedName).isPresent()) {
            throw new SPException(
                    HttpStatus.CONFLICT,
                    ResponseMessages.CATEGORY_ALREADY_EXISTS,
                    ResponseMessages.CATEGORY_ALREADY_EXISTS_CODE
            );
        }
        Category created = new Category();
        created.setName(trimmedName);
        created.setMainCategory(normalizeMainCategory(mainCategory));
        Category saved = categoryRepository.save(created);
        return new CategoryResponse(saved.getId(), saved.getName(), saved.getMainCategory());
    }

    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .map(category -> new CategoryResponse(
                        category.getId(),
                        category.getName(),
                        category.getMainCategory()
                ))
                .toList();
    }

    public CategoryResponse updateCategory(Long id, String name, String mainCategory) {
        if (id == null) {
            throw new SPException(
                    HttpStatus.BAD_REQUEST,
                    ResponseMessages.CATEGORY_NOT_FOUND,
                    ResponseMessages.CATEGORY_NOT_FOUND_CODE
            );
        }
        if (name == null || name.isBlank()) {
            throw new SPException(
                    HttpStatus.BAD_REQUEST,
                    ResponseMessages.CATEGORY_NAME_REQUIRED,
                    ResponseMessages.CATEGORY_NAME_REQUIRED_CODE
            );
        }
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new SPException(
                        HttpStatus.NOT_FOUND,
                        ResponseMessages.CATEGORY_NOT_FOUND,
                        ResponseMessages.CATEGORY_NOT_FOUND_CODE
                ));
        String trimmedName = name.trim();
        Category existing = categoryRepository.findByNameIgnoreCase(trimmedName).orElse(null);
        if (existing != null && !existing.getId().equals(category.getId())) {
            throw new SPException(
                    HttpStatus.CONFLICT,
                    ResponseMessages.CATEGORY_ALREADY_EXISTS,
                    ResponseMessages.CATEGORY_ALREADY_EXISTS_CODE
            );
        }
        category.setName(trimmedName);
        category.setMainCategory(normalizeMainCategory(mainCategory));
        Category saved = categoryRepository.save(category);
        return new CategoryResponse(saved.getId(), saved.getName(), saved.getMainCategory());
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new SPException(
                        HttpStatus.NOT_FOUND,
                        ResponseMessages.CATEGORY_NOT_FOUND,
                        ResponseMessages.CATEGORY_NOT_FOUND_CODE
                ));

        List<MarketplaceProduct> marketplaceProducts = marketplaceProductRepository.findByCategory(category);
        for (MarketplaceProduct marketplaceProduct : marketplaceProducts) {
            priceHistoryRepository.deleteByMarketplaceProductId(marketplaceProduct.getId());
        }
        if (!marketplaceProducts.isEmpty()) {
            marketplaceProductRepository.deleteAll(marketplaceProducts);
        }

        List<Product> products = productRepository.findByCategory(category);
        for (Product product : products) {
            priceHistoryRepository.deleteByProductId(product.getId());
        }
        if (!products.isEmpty()) {
            productRepository.deleteAll(products);
        }
        marketplaceManualMatchRepository.deleteByCategory(category);

        categoryRepository.delete(category);
    }

    public List<MarketplaceProductCandidateResponse> listMarketplaceCandidates(Long categoryId) {
        if (categoryId == null) {
            throw new SPException(
                    HttpStatus.BAD_REQUEST,
                    ResponseMessages.CATEGORY_NOT_FOUND,
                    ResponseMessages.CATEGORY_NOT_FOUND_CODE
            );
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new SPException(
                        HttpStatus.NOT_FOUND,
                        ResponseMessages.CATEGORY_NOT_FOUND,
                        ResponseMessages.CATEGORY_NOT_FOUND_CODE
                ));
        long startedAt = System.nanoTime();
        List<MarketplaceCategoryFetchService.MarketplaceProductCandidate> candidates =
                marketplaceCategorySearchService.fetchAll(category.getName());
        Map<String, String> mgBrandBySignature = buildMgBrandBySignature(candidates);
        List<MarketplaceProductCandidateResponse> responses = candidates.stream()
                .map(candidate -> {
                    String brandName = candidate.brandName();
                    if (candidate.marketplace() == Marketplace.YS && isBlank(brandName)) {
                        String signature = toProductSignature(candidate.name());
                        brandName = mgBrandBySignature.getOrDefault(
                                signature,
                                findMostSimilarBrand(signature, mgBrandBySignature)
                        );
                    }
                    return new MarketplaceProductCandidateResponse(
                            candidate.marketplace().getCode(),
                            candidate.externalId(),
                            candidate.name(),
                            brandName,
                            candidate.imageUrl(),
                            MarketplacePriceNormalizer.normalizeForDisplay(
                                    candidate.price()
                            ),
                            MarketplacePriceNormalizer.normalizeForDisplay(
                                    candidate.moneyPrice()
                            ),
                            MarketplacePriceNormalizer.normalizeForDisplay(
                                    candidate.basketDiscountThreshold()
                            ),
                            MarketplacePriceNormalizer.normalizeForDisplay(
                                    candidate.basketDiscountPrice()
                            ),
                            candidate.campaignBuyQuantity(),
                            candidate.campaignPayQuantity(),
                            MarketplacePriceNormalizer.normalizeForDisplay(
                                    candidate.effectivePrice()
                            ),
                            candidate.unit(),
                            candidate.unitValue(),
                            candidate.packCount()
                    );
                })
                .toList();
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "listMarketplaceCandidates timing: categoryId={}, candidateCount={}, durationMs={}",
                categoryId,
                responses.size(),
                durationMs
        );
        return responses;
    }


    public List<MarketplaceProductMatchPairResponse> matchMarketplaceProducts(
            Long categoryId,
            List<MarketplaceProductCandidateResponse> ys,
            List<MarketplaceProductCandidateResponse> mg,
            Double minScore
    ) {
        double threshold = minScore == null ? 0.76d : minScore;
        if (!Double.isFinite(threshold)) {
            threshold = 0.76d;
        }
        threshold = Math.clamp(threshold, 0d, 1d);
        List<MarketplaceProductCandidateResponse> safeYs = ys == null ? List.of() : ys;
        List<MarketplaceProductCandidateResponse> safeMg = mg == null ? List.of() : mg;
        List<MarketplaceProductMatchPairResponse> autoPairs =
                crossPlatformProductMatcherService.buildMarketplacePairs(safeYs, safeMg, threshold);
        return mergeManualMatches(categoryId, safeYs, safeMg, autoPairs);
    }

    @Transactional
    public void saveManualMarketplaceMatch(
            Long categoryId,
            String ysExternalId,
            String mgExternalId
    ) {
        Category category = requireCategory(categoryId);
        String normalizedYsExternalId = normalizeExternalId(ysExternalId);
        String normalizedMgExternalId = normalizeExternalId(mgExternalId);
        if (isBlank(normalizedYsExternalId) || isBlank(normalizedMgExternalId)) {
            throw new SPException(
                    HttpStatus.BAD_REQUEST,
                    "Manuel eslestirme icin her iki external id zorunludur.",
                    "MANUAL_MATCH_IDS_REQUIRED"
            );
        }
        if (marketplaceManualMatchRepository.findByCategoryIdAndYsExternalIdAndMgExternalId(
                categoryId,
                normalizedYsExternalId,
                normalizedMgExternalId
        ).isPresent()) {
            return;
        }
        marketplaceManualMatchRepository.deleteByCategoryIdAndYsExternalId(categoryId, normalizedYsExternalId);
        marketplaceManualMatchRepository.deleteByCategoryIdAndMgExternalId(categoryId, normalizedMgExternalId);
        MarketplaceManualMatch manualMatch = new MarketplaceManualMatch();
        manualMatch.setCategory(category);
        manualMatch.setYsExternalId(normalizedYsExternalId);
        manualMatch.setMgExternalId(normalizedMgExternalId);
        try {
            marketplaceManualMatchRepository.saveAndFlush(manualMatch);
        } catch (DataIntegrityViolationException ex) {
            if (marketplaceManualMatchRepository.findByCategoryIdAndYsExternalIdAndMgExternalId(
                    categoryId,
                    normalizedYsExternalId,
                    normalizedMgExternalId
            ).isPresent()) {
                return;
            }
            throw new SPException(
                    HttpStatus.CONFLICT,
                    "Manuel eslestirme kaydi cakisti, lutfen tekrar deneyin.",
                    "MANUAL_MATCH_CONFLICT"
            );
        }
    }

    @Transactional
    public void deleteManualMarketplaceMatch(
            Long categoryId,
            String ysExternalId,
            String mgExternalId
    ) {
        requireCategory(categoryId);
        String normalizedYsExternalId = normalizeExternalId(ysExternalId);
        String normalizedMgExternalId = normalizeExternalId(mgExternalId);
        if (isBlank(normalizedYsExternalId) || isBlank(normalizedMgExternalId)) {
            throw new SPException(
                    HttpStatus.BAD_REQUEST,
                    "Manuel eslestirme silmek icin her iki external id zorunludur.",
                    "MANUAL_MATCH_IDS_REQUIRED"
            );
        }
        marketplaceManualMatchRepository.deleteByCategoryIdAndYsExternalIdAndMgExternalId(
                categoryId,
                normalizedYsExternalId,
                normalizedMgExternalId
        );
    }

    @Transactional(readOnly = true)
    public List<MarketplaceProductEntryResponse> listMarketplaceAddedProducts(
            Long categoryId,
            String marketplaceCode
    ) {
        Category category = requireCategory(categoryId);
        Marketplace marketplace = resolveMarketplace(marketplaceCode);
        List<MarketplaceProduct> marketplaceProducts = findMarketplaceProducts(category, marketplace);

        if (marketplaceProducts.isEmpty()) {
            return List.of();
        }

        LatestHistoryData latestHistoryData = loadLatestHistoryData(marketplaceProducts);
        Map<String, MarketplaceCategoryFetchService.MarketplaceProductCandidate> candidateMap =
                shouldUseCandidateFallback(marketplaceProducts, latestHistoryData)
                        ? buildCandidateMap(category.getName())
                        : Map.of();
        List<MarketplaceProduct> productsToUpdate = new java.util.ArrayList<>();

        List<MarketplaceProductEntryResponse> responses = marketplaceProducts.stream()
                .map(marketplaceProduct -> toMarketplaceProductEntryResponse(
                        marketplaceProduct,
                        latestHistoryData,
                        candidateMap,
                        productsToUpdate
                ))
                .toList();
        if (!productsToUpdate.isEmpty()) {
            marketplaceProductRepository.saveAll(productsToUpdate);
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public List<MarketplaceProductAddedResponse> listAllMarketplaceAddedProducts(String marketplaceCode) {
        Marketplace marketplace = resolveMarketplace(marketplaceCode);
        List<MarketplaceProduct> marketplaceProducts = marketplace == null
                ? marketplaceProductRepository.findAll()
                : marketplaceProductRepository.findByMarketplace(marketplace);
        if (marketplaceProducts.isEmpty()) {
            return List.of();
        }
        LatestHistoryData latestHistoryData = loadLatestHistoryData(marketplaceProducts);
        Map<Long, List<MarketplaceProduct>> productsByCategory = marketplaceProducts.stream()
                .collect(Collectors.groupingBy(
                        product -> product.getCategory().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<MarketplaceProductAddedResponse> result = new java.util.ArrayList<>();
        List<MarketplaceProduct> productsToUpdate = new java.util.ArrayList<>();
        for (Map.Entry<Long, List<MarketplaceProduct>> entry : productsByCategory.entrySet()) {
            List<MarketplaceProduct> categoryProducts = entry.getValue();
            if (categoryProducts.isEmpty()) {
                continue;
            }
            String categoryName = categoryProducts.getFirst().getCategory().getName();
            Map<String, MarketplaceCategoryFetchService.MarketplaceProductCandidate> candidateMap =
                    shouldUseCandidateFallback(categoryProducts, latestHistoryData)
                            ? buildCandidateMap(categoryName)
                            : Map.of();
            for (MarketplaceProduct marketplaceProduct : categoryProducts) {
                MarketplaceProductEntryResponse response = toMarketplaceProductEntryResponse(
                        marketplaceProduct,
                        latestHistoryData,
                        candidateMap,
                        productsToUpdate
                );
                result.add(new MarketplaceProductAddedResponse(
                        entry.getKey(),
                        response.marketplaceCode(),
                        response.externalId(),
                        response.name(),
                        response.productId(),
                        response.brandName(),
                        response.imageUrl(),
                        response.price(),
                        response.moneyPrice(),
                        response.basketDiscountThreshold(),
                        response.basketDiscountPrice(),
                        response.campaignBuyQuantity(),
                        response.campaignPayQuantity(),
                        response.effectivePrice()
                ));
            }
        }
        if (!productsToUpdate.isEmpty()) {
            marketplaceProductRepository.saveAll(productsToUpdate);
        }
        return result;
    }

    private Category requireCategory(Long categoryId) {
        if (categoryId == null) {
            throw new SPException(
                    HttpStatus.BAD_REQUEST,
                    ResponseMessages.CATEGORY_NOT_FOUND,
                    ResponseMessages.CATEGORY_NOT_FOUND_CODE
            );
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new SPException(
                        HttpStatus.NOT_FOUND,
                        ResponseMessages.CATEGORY_NOT_FOUND,
                        ResponseMessages.CATEGORY_NOT_FOUND_CODE
                ));
    }

    private Marketplace resolveMarketplace(String marketplaceCode) {
        return MarketplaceCodeResolver.resolveNullable(marketplaceCode);
    }

    private List<MarketplaceProduct> findMarketplaceProducts(Category category, Marketplace marketplace) {
        if (marketplace == null) {
            return marketplaceProductRepository.findByCategory(category);
        }
        return marketplaceProductRepository.findByMarketplaceAndCategory(marketplace, category);
    }

    private Map<String, MarketplaceCategoryFetchService.MarketplaceProductCandidate> buildCandidateMap(
            String categoryName
    ) {
        List<MarketplaceCategoryFetchService.MarketplaceProductCandidate> candidates =
                marketplaceCategorySearchService.fetchAll(categoryName);
        Map<String, String> mgBrandBySignature = buildMgBrandBySignature(candidates);
        List<MarketplaceCategoryFetchService.MarketplaceProductCandidate> enriched = candidates.stream()
                .map(candidate -> enrichYsCandidateBrand(candidate, mgBrandBySignature))
                .toList();
        return enriched.stream()
                .collect(Collectors.toMap(
                        candidate -> candidateKey(candidate.marketplace().getCode(), candidate.externalId()),
                        candidate -> candidate,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private MarketplaceCategoryFetchService.MarketplaceProductCandidate enrichYsCandidateBrand(
            MarketplaceCategoryFetchService.MarketplaceProductCandidate candidate,
            Map<String, String> mgBrandBySignature
    ) {
        if (candidate.marketplace() != Marketplace.YS || !isBlank(candidate.brandName())) {
            return candidate;
        }
        String signature = toProductSignature(candidate.name());
        String inferredBrand = mgBrandBySignature.getOrDefault(
                signature,
                findMostSimilarBrand(signature, mgBrandBySignature)
        );
        if (isBlank(inferredBrand)) {
            return candidate;
        }
        return new MarketplaceCategoryFetchService.MarketplaceProductCandidate(
                candidate.marketplace(),
                candidate.externalId(),
                candidate.name(),
                inferredBrand,
                candidate.imageUrl(),
                candidate.price(),
                candidate.moneyPrice(),
                candidate.basketDiscountThreshold(),
                candidate.basketDiscountPrice(),
                candidate.campaignBuyQuantity(),
                candidate.campaignPayQuantity(),
                candidate.effectivePrice(),
                candidate.unit(),
                candidate.unitValue(),
                candidate.packCount()
        );
    }

    private LatestHistoryData loadLatestHistoryData(List<MarketplaceProduct> marketplaceProducts) {
        List<Long> ids = marketplaceProducts.stream()
                .map(MarketplaceProduct::getId)
                .toList();
        List<PriceHistory> histories = priceHistoryRepository.findByMarketplaceProductIds(ids);
        Map<Long, String> latestNames = new LinkedHashMap<>();
        Map<Long, Long> latestProductIds = new LinkedHashMap<>();
        Map<Long, BigDecimal> latestPrices = new LinkedHashMap<>();
        for (PriceHistory history : histories) {
            Long marketplaceProductId = history.getMarketplaceProduct().getId();
            latestNames.putIfAbsent(marketplaceProductId, history.getProduct().getName());
            latestProductIds.putIfAbsent(marketplaceProductId, history.getProduct().getId());
            latestPrices.putIfAbsent(marketplaceProductId, history.getPrice());
        }
        return new LatestHistoryData(latestNames, latestProductIds, latestPrices);
    }

    private MarketplaceProductEntryResponse toMarketplaceProductEntryResponse(
            MarketplaceProduct marketplaceProduct,
            LatestHistoryData latestHistoryData,
            Map<String, MarketplaceCategoryFetchService.MarketplaceProductCandidate> candidateMap,
            List<MarketplaceProduct> productsToUpdate
    ) {
        Long marketplaceProductId = marketplaceProduct.getId();
        String latestName = latestHistoryData.latestNames().getOrDefault(marketplaceProductId, "");
        Long latestProductId = latestHistoryData.latestProductIds().get(marketplaceProductId);
        BigDecimal latestPrice = latestHistoryData.latestPrices().get(marketplaceProductId);

        String key = candidateKey(
                marketplaceProduct.getMarketplace().getCode(),
                marketplaceProduct.getExternalId()
        );
        CandidateData candidateData = toCandidateData(candidateMap.get(key));
        BigDecimal normalizedLatestPrice = MarketplacePriceNormalizer.normalizeForDisplay(
                latestPrice
        );
        BigDecimal normalizedCandidatePrice = MarketplacePriceNormalizer.normalizeForDisplay(
                candidateData.price()
        );
        BigDecimal normalizedCandidateMoneyPrice = MarketplacePriceNormalizer.normalizeForDisplay(
                candidateData.moneyPrice()
        );
        BigDecimal normalizedCandidateBasketDiscountThreshold = MarketplacePriceNormalizer.normalizeForDisplay(
                candidateData.basketDiscountThreshold()
        );
        BigDecimal normalizedCandidateBasketDiscountPrice = MarketplacePriceNormalizer.normalizeForDisplay(
                candidateData.basketDiscountPrice()
        );
        BigDecimal normalizedCandidateEffectivePrice = MarketplacePriceNormalizer.normalizeForDisplay(
                candidateData.effectivePrice()
        );
        String brandName = isBlank(marketplaceProduct.getBrandName())
                ? candidateData.brandName()
                : marketplaceProduct.getBrandName();
        String imageUrl = isBlank(marketplaceProduct.getImageUrl())
                ? candidateData.imageUrl()
                : marketplaceProduct.getImageUrl();
        applyCandidateFallbacks(
                marketplaceProduct,
                candidateData,
                normalizedCandidateMoneyPrice,
                normalizedCandidateBasketDiscountThreshold,
                normalizedCandidateBasketDiscountPrice,
                normalizedCandidateEffectivePrice,
                productsToUpdate
        );
        BigDecimal normalizedStoredMoneyPrice = MarketplacePriceNormalizer.normalizeForDisplay(
                marketplaceProduct.getMoneyPrice()
        );
        BigDecimal normalizedStoredBasketDiscountThreshold = MarketplacePriceNormalizer.normalizeForDisplay(
                marketplaceProduct.getBasketDiscountThreshold()
        );
        BigDecimal normalizedStoredBasketDiscountPrice = MarketplacePriceNormalizer.normalizeForDisplay(
                marketplaceProduct.getBasketDiscountPrice()
        );
        BigDecimal normalizedStoredEffectivePrice = MarketplacePriceNormalizer.normalizeForDisplay(
                marketplaceProduct.getEffectivePrice()
        );
        String resolvedName = resolveDisplayName(
                latestName,
                candidateData.fallbackName(),
                brandName,
                marketplaceProduct.getExternalId()
        );
        return new MarketplaceProductEntryResponse(
                marketplaceProduct.getMarketplace().getCode(),
                marketplaceProduct.getExternalId(),
                resolvedName,
                latestProductId,
                brandName,
                imageUrl,
                normalizedLatestPrice != null ? normalizedLatestPrice : normalizedCandidatePrice,
                normalizedStoredMoneyPrice != null
                        ? normalizedStoredMoneyPrice
                        : normalizedCandidateMoneyPrice,
                normalizedStoredBasketDiscountThreshold != null
                        ? normalizedStoredBasketDiscountThreshold
                        : normalizedCandidateBasketDiscountThreshold,
                normalizedStoredBasketDiscountPrice != null
                        ? normalizedStoredBasketDiscountPrice
                        : normalizedCandidateBasketDiscountPrice,
                marketplaceProduct.getCampaignBuyQuantity() != null
                        ? marketplaceProduct.getCampaignBuyQuantity()
                        : candidateData.campaignBuyQuantity(),
                marketplaceProduct.getCampaignPayQuantity() != null
                        ? marketplaceProduct.getCampaignPayQuantity()
                        : candidateData.campaignPayQuantity(),
                normalizedStoredEffectivePrice != null
                        ? normalizedStoredEffectivePrice
                        : normalizedCandidateEffectivePrice
        );
    }

    private void applyCandidateFallbacks(
            MarketplaceProduct marketplaceProduct,
            CandidateData candidateData,
            BigDecimal normalizedCandidateMoneyPrice,
            BigDecimal normalizedCandidateBasketDiscountThreshold,
            BigDecimal normalizedCandidateBasketDiscountPrice,
            BigDecimal normalizedCandidateEffectivePrice,
            List<MarketplaceProduct> productsToUpdate
    ) {
        applyFallback(
                isBlank(marketplaceProduct.getBrandName()) && !isBlank(candidateData.brandName()),
                () -> marketplaceProduct.setBrandName(candidateData.brandName()),
                productsToUpdate,
                marketplaceProduct
        );
        applyFallback(
                isBlank(marketplaceProduct.getImageUrl()) && !isBlank(candidateData.imageUrl()),
                () -> marketplaceProduct.setImageUrl(candidateData.imageUrl()),
                productsToUpdate,
                marketplaceProduct
        );
        applyFallback(
                marketplaceProduct.getMoneyPrice() == null && normalizedCandidateMoneyPrice != null,
                () -> marketplaceProduct.setMoneyPrice(normalizedCandidateMoneyPrice),
                productsToUpdate,
                marketplaceProduct
        );
        applyFallback(
                marketplaceProduct.getBasketDiscountThreshold() == null
                        && normalizedCandidateBasketDiscountThreshold != null,
                () -> marketplaceProduct.setBasketDiscountThreshold(normalizedCandidateBasketDiscountThreshold),
                productsToUpdate,
                marketplaceProduct
        );
        applyFallback(
                marketplaceProduct.getBasketDiscountPrice() == null && normalizedCandidateBasketDiscountPrice != null,
                () -> marketplaceProduct.setBasketDiscountPrice(normalizedCandidateBasketDiscountPrice),
                productsToUpdate,
                marketplaceProduct
        );
        applyFallback(
                marketplaceProduct.getCampaignBuyQuantity() == null && candidateData.campaignBuyQuantity() != null,
                () -> marketplaceProduct.setCampaignBuyQuantity(candidateData.campaignBuyQuantity()),
                productsToUpdate,
                marketplaceProduct
        );
        applyFallback(
                marketplaceProduct.getCampaignPayQuantity() == null && candidateData.campaignPayQuantity() != null,
                () -> marketplaceProduct.setCampaignPayQuantity(candidateData.campaignPayQuantity()),
                productsToUpdate,
                marketplaceProduct
        );
        applyFallback(
                marketplaceProduct.getEffectivePrice() == null && normalizedCandidateEffectivePrice != null,
                () -> marketplaceProduct.setEffectivePrice(normalizedCandidateEffectivePrice),
                productsToUpdate,
                marketplaceProduct
        );
    }

    private void applyFallback(
            boolean condition,
            Runnable updater,
            List<MarketplaceProduct> productsToUpdate,
            MarketplaceProduct marketplaceProduct
    ) {
        if (!condition) {
            return;
        }
        updater.run();
        markForUpdate(productsToUpdate, marketplaceProduct);
    }

    private void markForUpdate(List<MarketplaceProduct> productsToUpdate, MarketplaceProduct marketplaceProduct) {
        if (!productsToUpdate.contains(marketplaceProduct)) {
            productsToUpdate.add(marketplaceProduct);
        }
    }

    private CandidateData toCandidateData(
            MarketplaceCategoryFetchService.MarketplaceProductCandidate candidate
    ) {
        if (candidate == null) {
            return new CandidateData("", "", "", null, null, null, null, null, null, null);
        }
        return new CandidateData(
                candidate.name(),
                candidate.brandName(),
                candidate.imageUrl(),
                candidate.price(),
                candidate.moneyPrice(),
                candidate.basketDiscountThreshold(),
                candidate.basketDiscountPrice(),
                candidate.campaignBuyQuantity(),
                candidate.campaignPayQuantity(),
                candidate.effectivePrice()
        );
    }

    private Map<String, String> buildMgBrandBySignature(
            List<MarketplaceCategoryFetchService.MarketplaceProductCandidate> candidates
    ) {
        Map<String, String> brandBySignature = new LinkedHashMap<>();
        candidates.stream()
                .filter(candidate -> candidate.marketplace() == Marketplace.MG)
                .filter(candidate -> !isBlank(candidate.brandName()))
                .forEach(candidate -> brandBySignature.putIfAbsent(
                        toProductSignature(candidate.name()),
                        candidate.brandName()
                ));
        return brandBySignature;
    }

    private String findMostSimilarBrand(String ysSignature, Map<String, String> mgBrandBySignature) {
        if (isBlank(ysSignature) || mgBrandBySignature.isEmpty()) {
            return "";
        }
        Set<String> ysTokens = tokenSet(ysSignature);
        if (ysTokens.isEmpty()) {
            return "";
        }
        double bestScore = 0.0d;
        String bestBrand = "";
        for (Map.Entry<String, String> entry : mgBrandBySignature.entrySet()) {
            Set<String> mgTokens = tokenSet(entry.getKey());
            if (mgTokens.isEmpty()) {
                continue;
            }
            double score = jaccard(ysTokens, mgTokens);
            if (score > bestScore) {
                bestScore = score;
                bestBrand = entry.getValue();
            }
        }
        return bestScore >= 0.70d ? bestBrand : "";
    }

    private double jaccard(Set<String> left, Set<String> right) {
        long intersection = left.stream().filter(right::contains).count();
        long union = left.size() + right.size() - intersection;
        if (union == 0L) {
            return 0.0d;
        }
        return (double) intersection / (double) union;
    }

    private Set<String> tokenSet(String value) {
        if (isBlank(value)) {
            return Set.of();
        }
        return Stream.of(value.split(" "))
                .filter(token -> token.length() > 1)
                .collect(Collectors.toSet());
    }

    private String toProductSignature(String productName) {
        if (isBlank(productName)) {
            return "";
        }
        String lower = productName.toLowerCase(Locale.forLanguageTag("tr-TR"));
        String withoutDiacritics = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return stripQuantityTokens(stripPackSignatureTokens(withoutDiacritics))
                .replaceAll("\\bpaket\\b", " ")
                .replaceAll("\\badet\\b", " ")
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String stripQuantityTokens(String value) {
        StringBuilder builder = new StringBuilder();
        String[] tokens = value.split("\\s+");
        int index = 0;
        while (index < tokens.length) {
            int span = quantityTokenSpan(tokens, index);
            if (span == 0) {
                appendToken(builder, tokens[index]);
                index++;
                continue;
            }
            index += span;
        }
        return builder.toString();
    }

    private String stripPackSignatureTokens(String value) {
        StringBuilder builder = new StringBuilder();
        String[] tokens = value.replace('’', ' ').replace('\'', ' ').split("\\s+");
        int index = 0;
        while (index < tokens.length) {
            int span = packSignatureTokenSpan(tokens, index);
            if (span == 0) {
                appendToken(builder, tokens[index]);
                index++;
                continue;
            }
            index += span;
        }
        return builder.toString();
    }

    private int quantityTokenSpan(String[] tokens, int index) {
        String current = tokens[index];
        if (isCombinedQuantityToken(current)) {
            return 1;
        }
        String next = index + 1 < tokens.length ? tokens[index + 1] : "";
        return isQuantityNumberToken(current) && isQuantityUnitToken(next) ? 2 : 0;
    }

    private int packSignatureTokenSpan(String[] tokens, int index) {
        String current = tokens[index];
        if (isCombinedPackSignatureToken(current)) {
            return 1;
        }
        if (!isPositiveIntegerToken(current)) {
            return 0;
        }
        String next = index + 1 < tokens.length ? tokens[index + 1] : "";
        String trailing = index + 2 < tokens.length ? tokens[index + 2] : "";
        if (!isPackSignatureToken(next, trailing)) {
            return 0;
        }
        return ("li".equals(next) || "lu".equals(next)) && "paket".equals(trailing) ? 3 : 2;
    }

    private void appendToken(StringBuilder builder, String token) {
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(token);
    }

    private boolean isQuantityNumberToken(String token) {
        if (isBlank(token)) {
            return false;
        }
        try {
            Double.parseDouble(token.replace(',', '.'));
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean isQuantityUnitToken(String token) {
        return switch (token) {
            case "g", "gr", "kg", "ml", "l", "lt" -> true;
            default -> false;
        };
    }

    private boolean isCombinedQuantityToken(String token) {
        if (isBlank(token) || token.length() < 2) {
            return false;
        }
        for (String unit : List.of("kg", "gr", "ml", "lt", "g", "l")) {
            if (!token.endsWith(unit)) {
                continue;
            }
            String numericPart = token.substring(0, token.length() - unit.length());
            return isQuantityNumberToken(numericPart);
        }
        return false;
    }

    private boolean isPositiveIntegerToken(String token) {
        if (isBlank(token)) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isPackSignatureToken(String current, String next) {
        return "li".equals(current)
                || "lu".equals(current)
                || (("li".equals(current) || "lu".equals(current)) && "paket".equals(next));
    }

    private boolean isCombinedPackSignatureToken(String token) {
        if (isBlank(token) || token.length() < 3) {
            return false;
        }
        for (String suffix : List.of("li", "lu")) {
            if (!token.endsWith(suffix)) {
                continue;
            }
            return isPositiveIntegerToken(token.substring(0, token.length() - suffix.length()));
        }
        return false;
    }

    private boolean shouldUseCandidateFallback(
            List<MarketplaceProduct> marketplaceProducts,
            LatestHistoryData latestHistoryData
    ) {
        return marketplaceProducts.stream().anyMatch(marketplaceProduct -> {
            Long marketplaceProductId = marketplaceProduct.getId();
            boolean missingHistoryName = isBlank(latestHistoryData.latestNames().get(marketplaceProductId));
            boolean missingHistoryPrice = latestHistoryData.latestPrices().get(marketplaceProductId) == null;
            boolean missingMoneyPrice = marketplaceProduct.getMarketplace() == Marketplace.MG
                    && marketplaceProduct.getMoneyPrice() == null;
            return isBlank(marketplaceProduct.getBrandName())
                    || isBlank(marketplaceProduct.getImageUrl())
                    || missingHistoryName
                    || missingHistoryPrice
                    || missingMoneyPrice;
        });
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveDisplayName(
            String latestName,
            String fallbackName,
            String brandName,
            String externalId
    ) {
        if (!isBlank(latestName)) {
            return latestName.trim();
        }
        if (!isBlank(fallbackName)) {
            return fallbackName.trim();
        }
        if (!isBlank(brandName)) {
            return brandName.trim();
        }
        return "Urun " + (externalId == null ? "" : externalId.trim());
    }

    private String normalizeExternalId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private List<MarketplaceProductMatchPairResponse> mergeManualMatches(
            Long categoryId,
            List<MarketplaceProductCandidateResponse> ys,
            List<MarketplaceProductCandidateResponse> mg,
            List<MarketplaceProductMatchPairResponse> autoPairs
    ) {
        if (categoryId == null) {
            return autoPairs;
        }
        Map<String, MarketplaceProductCandidateResponse> ysByExternalId = ys.stream()
                .collect(Collectors.toMap(
                        candidate -> normalizeExternalId(candidate.externalId()),
                        candidate -> candidate,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, MarketplaceProductCandidateResponse> mgByExternalId = mg.stream()
                .collect(Collectors.toMap(
                        candidate -> normalizeExternalId(candidate.externalId()),
                        candidate -> candidate,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<MarketplaceProductMatchPairResponse> manualPairs = marketplaceManualMatchRepository.findByCategoryId(categoryId)
                .stream()
                .map(manualMatch -> toManualMatchPair(manualMatch, ysByExternalId, mgByExternalId))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (manualPairs.isEmpty()) {
            return autoPairs;
        }
        Set<String> usedYs = manualPairs.stream()
                .map(pair -> normalizeExternalId(pair.ys().externalId()))
                .collect(Collectors.toSet());
        Set<String> usedMg = manualPairs.stream()
                .map(pair -> normalizeExternalId(pair.mg().externalId()))
                .collect(Collectors.toSet());
        List<MarketplaceProductMatchPairResponse> merged = new java.util.ArrayList<>(manualPairs);
        for (MarketplaceProductMatchPairResponse autoPair : autoPairs) {
            if (usedYs.contains(normalizeExternalId(autoPair.ys().externalId())) ||
                    usedMg.contains(normalizeExternalId(autoPair.mg().externalId()))) {
                continue;
            }
            merged.add(autoPair);
        }
        return merged;
    }

    private MarketplaceProductMatchPairResponse toManualMatchPair(
            MarketplaceManualMatch manualMatch,
            Map<String, MarketplaceProductCandidateResponse> ysByExternalId,
            Map<String, MarketplaceProductCandidateResponse> mgByExternalId
    ) {
        MarketplaceProductCandidateResponse ys = ysByExternalId.get(normalizeExternalId(manualMatch.getYsExternalId()));
        MarketplaceProductCandidateResponse mg = mgByExternalId.get(normalizeExternalId(manualMatch.getMgExternalId()));
        if (ys == null || mg == null) {
            return null;
        }
        MarketplaceProductMatchScoreResponse manualScore = new MarketplaceProductMatchScoreResponse(
                1d,
                1d,
                1d,
                1d,
                1d,
                1d,
                1d,
                1d,
                1d
        );
        return new MarketplaceProductMatchPairResponse(ys, mg, manualScore, true, true);
    }

    private String normalizeMainCategory(String mainCategory) {
        if (mainCategory == null) {
            return null;
        }
        String trimmed = mainCategory.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String candidateKey(String marketplaceCode, String externalId) {
        String normalizedMarketplace = marketplaceCode == null ? "" : marketplaceCode.trim().toUpperCase(Locale.ROOT);
        return normalizedMarketplace + ":" + normalizeExternalId(externalId);
    }

    private record LatestHistoryData(
            Map<Long, String> latestNames,
            Map<Long, Long> latestProductIds,
            Map<Long, BigDecimal> latestPrices
    ) {
    }

    private record CandidateData(
            String fallbackName,
            String brandName,
            String imageUrl,
            BigDecimal price,
            BigDecimal moneyPrice,
            BigDecimal basketDiscountThreshold,
            BigDecimal basketDiscountPrice,
            Integer campaignBuyQuantity,
            Integer campaignPayQuantity,
            BigDecimal effectivePrice
    ) {
    }

}
