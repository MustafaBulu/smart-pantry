package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.dto.response.CategoryResponse;
import com.mustafabulu.smartpantry.dto.response.MarketplaceProductCandidateResponse;
import com.mustafabulu.smartpantry.dto.response.MarketplaceProductEntryResponse;
import com.mustafabulu.smartpantry.core.exception.SPException;
import com.mustafabulu.smartpantry.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.core.util.MarketplacePriceNormalizer;
import com.mustafabulu.smartpantry.core.util.MarketplaceCodeResolver;
import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.model.PriceHistory;
import com.mustafabulu.smartpantry.model.Product;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.repository.ProductRepository;
import com.mustafabulu.smartpantry.repository.PriceHistoryRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Service
@AllArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MarketplaceProductRepository marketplaceProductRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MarketplaceCategorySearchService marketplaceCategorySearchService;

    public CategoryResponse createCategory(String name) {
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
        Category saved = categoryRepository.save(created);
        return new CategoryResponse(saved.getId(), saved.getName());
    }

    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .map(category -> new CategoryResponse(category.getId(), category.getName()))
                .toList();
    }

    public CategoryResponse updateCategory(Long id, String name) {
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
        Category saved = categoryRepository.save(category);
        return new CategoryResponse(saved.getId(), saved.getName());
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
                                    candidate.marketplace(),
                                    candidate.price()
                            ),
                            MarketplacePriceNormalizer.normalizeForDisplay(
                                    candidate.marketplace(),
                                    candidate.moneyPrice()
                            ),
                            MarketplacePriceNormalizer.normalizeForDisplay(
                                    candidate.marketplace(),
                                    candidate.basketDiscountThreshold()
                            ),
                            MarketplacePriceNormalizer.normalizeForDisplay(
                                    candidate.marketplace(),
                                    candidate.basketDiscountPrice()
                            ),
                            candidate.campaignBuyQuantity(),
                            candidate.campaignPayQuantity(),
                            MarketplacePriceNormalizer.normalizeForDisplay(
                                    candidate.marketplace(),
                                    candidate.effectivePrice()
                            )
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
                candidate.effectivePrice()
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
        MarketplaceCategoryFetchService.MarketplaceProductCandidate candidate = candidateMap.get(key);
        String fallbackName = candidate == null ? "" : candidate.name();
        String candidateBrandName = candidate == null ? "" : candidate.brandName();
        String candidateImageUrl = candidate == null ? "" : candidate.imageUrl();
        BigDecimal candidateMoneyPrice = candidate == null ? null : candidate.moneyPrice();
        BigDecimal candidateBasketDiscountThreshold = candidate == null ? null : candidate.basketDiscountThreshold();
        BigDecimal candidateBasketDiscountPrice = candidate == null ? null : candidate.basketDiscountPrice();
        Integer candidateCampaignBuyQuantity = candidate == null ? null : candidate.campaignBuyQuantity();
        Integer candidateCampaignPayQuantity = candidate == null ? null : candidate.campaignPayQuantity();
        BigDecimal candidateEffectivePrice = candidate == null ? null : candidate.effectivePrice();
        BigDecimal candidatePrice = candidate == null ? null : candidate.price();
        Marketplace marketplace = marketplaceProduct.getMarketplace();
        BigDecimal normalizedLatestPrice = MarketplacePriceNormalizer.normalizeForDisplay(
                marketplace,
                latestPrice
        );
        BigDecimal normalizedCandidatePrice = MarketplacePriceNormalizer.normalizeForDisplay(
                marketplace,
                candidatePrice
        );
        BigDecimal normalizedCandidateMoneyPrice = MarketplacePriceNormalizer.normalizeForDisplay(
                marketplace,
                candidateMoneyPrice
        );
        BigDecimal normalizedCandidateBasketDiscountThreshold = MarketplacePriceNormalizer.normalizeForDisplay(
                marketplace,
                candidateBasketDiscountThreshold
        );
        BigDecimal normalizedCandidateBasketDiscountPrice = MarketplacePriceNormalizer.normalizeForDisplay(
                marketplace,
                candidateBasketDiscountPrice
        );
        BigDecimal normalizedCandidateEffectivePrice = MarketplacePriceNormalizer.normalizeForDisplay(
                marketplace,
                candidateEffectivePrice
        );
        String brandName = isBlank(marketplaceProduct.getBrandName())
                ? candidateBrandName
                : marketplaceProduct.getBrandName();
        String imageUrl = isBlank(marketplaceProduct.getImageUrl())
                ? candidateImageUrl
                : marketplaceProduct.getImageUrl();
        if (isBlank(marketplaceProduct.getBrandName()) && !isBlank(candidateBrandName)) {
            marketplaceProduct.setBrandName(candidateBrandName);
            productsToUpdate.add(marketplaceProduct);
        }
        if (isBlank(marketplaceProduct.getImageUrl()) && !isBlank(candidateImageUrl)) {
            marketplaceProduct.setImageUrl(candidateImageUrl);
            if (!productsToUpdate.contains(marketplaceProduct)) {
                productsToUpdate.add(marketplaceProduct);
            }
        }
        if (marketplaceProduct.getMoneyPrice() == null && normalizedCandidateMoneyPrice != null) {
            marketplaceProduct.setMoneyPrice(normalizedCandidateMoneyPrice);
            if (!productsToUpdate.contains(marketplaceProduct)) {
                productsToUpdate.add(marketplaceProduct);
            }
        }
        if (marketplaceProduct.getBasketDiscountThreshold() == null
                && normalizedCandidateBasketDiscountThreshold != null) {
            marketplaceProduct.setBasketDiscountThreshold(normalizedCandidateBasketDiscountThreshold);
            if (!productsToUpdate.contains(marketplaceProduct)) {
                productsToUpdate.add(marketplaceProduct);
            }
        }
        if (marketplaceProduct.getBasketDiscountPrice() == null && normalizedCandidateBasketDiscountPrice != null) {
            marketplaceProduct.setBasketDiscountPrice(normalizedCandidateBasketDiscountPrice);
            if (!productsToUpdate.contains(marketplaceProduct)) {
                productsToUpdate.add(marketplaceProduct);
            }
        }
        if (marketplaceProduct.getCampaignBuyQuantity() == null && candidateCampaignBuyQuantity != null) {
            marketplaceProduct.setCampaignBuyQuantity(candidateCampaignBuyQuantity);
            if (!productsToUpdate.contains(marketplaceProduct)) {
                productsToUpdate.add(marketplaceProduct);
            }
        }
        if (marketplaceProduct.getCampaignPayQuantity() == null && candidateCampaignPayQuantity != null) {
            marketplaceProduct.setCampaignPayQuantity(candidateCampaignPayQuantity);
            if (!productsToUpdate.contains(marketplaceProduct)) {
                productsToUpdate.add(marketplaceProduct);
            }
        }
        if (marketplaceProduct.getEffectivePrice() == null && normalizedCandidateEffectivePrice != null) {
            marketplaceProduct.setEffectivePrice(normalizedCandidateEffectivePrice);
            if (!productsToUpdate.contains(marketplaceProduct)) {
                productsToUpdate.add(marketplaceProduct);
            }
        }
        BigDecimal normalizedStoredMoneyPrice = MarketplacePriceNormalizer.normalizeForDisplay(
                marketplace,
                marketplaceProduct.getMoneyPrice()
        );
        BigDecimal normalizedStoredBasketDiscountThreshold = MarketplacePriceNormalizer.normalizeForDisplay(
                marketplace,
                marketplaceProduct.getBasketDiscountThreshold()
        );
        BigDecimal normalizedStoredBasketDiscountPrice = MarketplacePriceNormalizer.normalizeForDisplay(
                marketplace,
                marketplaceProduct.getBasketDiscountPrice()
        );
        BigDecimal normalizedStoredEffectivePrice = MarketplacePriceNormalizer.normalizeForDisplay(
                marketplace,
                marketplaceProduct.getEffectivePrice()
        );
        return new MarketplaceProductEntryResponse(
                marketplaceProduct.getMarketplace().getCode(),
                marketplaceProduct.getExternalId(),
                latestName.isBlank() ? fallbackName : latestName,
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
                        : candidateCampaignBuyQuantity,
                marketplaceProduct.getCampaignPayQuantity() != null
                        ? marketplaceProduct.getCampaignPayQuantity()
                        : candidateCampaignPayQuantity,
                normalizedStoredEffectivePrice != null
                        ? normalizedStoredEffectivePrice
                        : normalizedCandidateEffectivePrice
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
        return List.of(value.split(" ")).stream()
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
        return withoutDiacritics
                .replaceAll("\\d+[\\.,]?\\d*\\s*(g|gr|kg|ml|l|lt)\\b", " ")
                .replaceAll("\\d+\\s*['’]?(li|lu|lu paket|li paket)\\b", " ")
                .replaceAll("\\bpaket\\b", " ")
                .replaceAll("\\badet\\b", " ")
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
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

    private String candidateKey(String marketplaceCode, String externalId) {
        return marketplaceCode + ":" + externalId;
    }

    private record LatestHistoryData(
            Map<Long, String> latestNames,
            Map<Long, Long> latestProductIds,
            Map<Long, BigDecimal> latestPrices
    ) {
    }

}
