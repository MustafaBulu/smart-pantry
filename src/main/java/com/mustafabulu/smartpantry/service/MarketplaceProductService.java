package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.dto.response.BulkAddResponse;
import com.mustafabulu.smartpantry.dto.response.BulkAddResultItem;
import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.migros.service.MigrosProductDetailsService;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.repository.PriceHistoryRepository;
import com.mustafabulu.smartpantry.yemeksepeti.service.YemeksepetiProductDetailsService;
import com.mustafabulu.smartpantry.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.config.MarketplaceUrlProperties;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.mustafabulu.smartpantry.core.exception.SPException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class MarketplaceProductService {

    private final CategoryRepository categoryRepository;
    private final MarketplaceProductRepository marketplaceProductRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MigrosProductDetailsService migrosProductDetailsService;
    private final YemeksepetiProductDetailsService yemeksepetiProductDetailsService;
    private final MarketplaceUrlProperties marketplaceUrlProperties;

    public AddProductResult addProduct(String marketplaceCode, String categoryName, String productId) {
        Marketplace marketplace = Marketplace.fromCode(marketplaceCode);
        if (marketplace == null) {
            return AddProductResult.badRequest(ResponseMessages.INVALID_MARKETPLACE_CODE);
        }

        Category category = categoryRepository.findByNameIgnoreCase(categoryName.trim())
                .orElse(null);
        if (category == null) {
            return AddProductResult.notFound(ResponseMessages.CATEGORY_NOT_FOUND);
        }

        String externalId = productId.trim();
        String productUrl = buildProductUrl(marketplace, externalId);

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
            marketplaceProduct = marketplaceProductRepository.save(created);
            createdNew = true;
        }

        boolean recorded;
        try {
            recorded = switch (marketplace) {
                case YS -> yemeksepetiProductDetailsService.recordDetailsForProduct(category, marketplaceProduct);
                case MG -> migrosProductDetailsService.recordDetailsForProduct(category, marketplaceProduct);
            };
        } catch (SPException ex) {
            if (createdNew) {
                marketplaceProductRepository.delete(marketplaceProduct);
            }
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                return AddProductResult.notFound(ResponseMessages.PRODUCT_COULD_NOT_BE_ADDED);
            }
            return AddProductResult.badRequest(ResponseMessages.PRODUCT_COULD_NOT_BE_ADDED);
        }

        if (!recorded) {
            if (createdNew) {
                marketplaceProductRepository.delete(marketplaceProduct);
            }
            return AddProductResult.badRequest(ResponseMessages.PRODUCT_COULD_NOT_BE_ADDED);
        }

        return AddProductResult.status(createdNew ? HttpStatus.CREATED : HttpStatus.OK);
    }

    private String buildProductUrl(Marketplace marketplace, String externalId) {
        return switch (marketplace) {
            case YS -> marketplaceUrlProperties.getYemeksepetiBase() + externalId;
            case MG -> marketplaceUrlProperties.getMigrosPrefix()
                    + externalId
                    + marketplaceUrlProperties.getMigrosSuffix();
        };
    }

    public BulkAddResponse addProducts(String marketplaceCode, String categoryName, List<String> productIds) {
        List<BulkAddResultItem> results = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int failed = 0;
        for (String productId : productIds) {
            if (productId == null || productId.isBlank()) {
                failed++;
                results.add(new BulkAddResultItem(productId, HttpStatus.BAD_REQUEST.value(), ResponseMessages.PRODUCT_COULD_NOT_BE_ADDED));
                continue;
            }
            AddProductResult result = addProduct(marketplaceCode, categoryName, productId);
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
        boolean recorded = switch (marketplace) {
            case YS -> yemeksepetiProductDetailsService.recordDetailsForProduct(category, marketplaceProduct);
            case MG -> migrosProductDetailsService.recordDetailsForProduct(category, marketplaceProduct);
        };
        if (!recorded) {
            return RefreshProductResult.badRequest(ResponseMessages.MARKETPLACE_REFRESH_FAILED);
        }
        return RefreshProductResult.ok();
    }

    public record AddProductResult(HttpStatus status, String message) {
        static AddProductResult badRequest(String message) {
            return new AddProductResult(HttpStatus.BAD_REQUEST, message);
        }

        static AddProductResult notFound(String message) {
            return new AddProductResult(HttpStatus.NOT_FOUND, message);
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
            return new DeleteMarketplaceProductResult(HttpStatus.OK, ResponseMessages.MARKETPLACE_PRODUCT_REMOVED, count);
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
