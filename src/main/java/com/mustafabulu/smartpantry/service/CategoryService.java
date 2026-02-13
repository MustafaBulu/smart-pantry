package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.dto.response.CategoryResponse;
import com.mustafabulu.smartpantry.dto.response.MarketplaceProductCandidateResponse;
import com.mustafabulu.smartpantry.dto.response.MarketplaceProductEntryResponse;
import com.mustafabulu.smartpantry.core.exception.SPException;
import com.mustafabulu.smartpantry.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.core.util.MarketplaceCodeResolver;
import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.model.PriceHistory;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.repository.ProductRepository;
import com.mustafabulu.smartpantry.repository.PriceHistoryRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
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

    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new SPException(
                        HttpStatus.NOT_FOUND,
                        ResponseMessages.CATEGORY_NOT_FOUND,
                        ResponseMessages.CATEGORY_NOT_FOUND_CODE
                ));
        if (productRepository.existsByCategory(category)
                || marketplaceProductRepository.existsByCategory(category)) {
            throw new SPException(
                    HttpStatus.CONFLICT,
                    ResponseMessages.CATEGORY_IN_USE,
                    ResponseMessages.CATEGORY_IN_USE_CODE
            );
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
        return marketplaceCategorySearchService.fetchAll(category.getName()).stream()
                .map(candidate -> new MarketplaceProductCandidateResponse(
                        candidate.marketplace().getCode(),
                        candidate.externalId(),
                        candidate.name(),
                        candidate.brandName(),
                        candidate.imageUrl(),
                        candidate.price()
                ))
                .toList();
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

        Map<String, MarketplaceCategoryFetchService.MarketplaceProductCandidate> candidateMap =
                buildCandidateMap(category.getName());
        LatestHistoryData latestHistoryData = loadLatestHistoryData(marketplaceProducts);

        return marketplaceProducts.stream()
                .map(marketplaceProduct -> toMarketplaceProductEntryResponse(
                        marketplaceProduct,
                        latestHistoryData,
                        candidateMap
                ))
                .toList();
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
        return marketplaceCategorySearchService.fetchAll(categoryName).stream()
                .collect(Collectors.toMap(
                        candidate -> candidateKey(candidate.marketplace().getCode(), candidate.externalId()),
                        candidate -> candidate,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
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
            Map<String, MarketplaceCategoryFetchService.MarketplaceProductCandidate> candidateMap
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
        String brandName = candidate == null ? "" : candidate.brandName();
        String imageUrl = candidate == null ? "" : candidate.imageUrl();
        BigDecimal candidatePrice = candidate == null ? null : candidate.price();

        return new MarketplaceProductEntryResponse(
                marketplaceProduct.getMarketplace().getCode(),
                marketplaceProduct.getExternalId(),
                latestName.isBlank() ? fallbackName : latestName,
                latestProductId,
                brandName,
                imageUrl,
                latestPrice != null ? latestPrice : candidatePrice
        );
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

