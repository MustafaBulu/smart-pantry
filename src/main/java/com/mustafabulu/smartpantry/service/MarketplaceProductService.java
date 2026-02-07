package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.migros.service.MigrosProductDetailsService;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.yemeksepeti.service.YemeksepetiProductDetailsService;
import com.mustafabulu.smartpantry.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.config.MarketplaceUrlProperties;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.mustafabulu.smartpantry.core.exception.SPException;

@Service
@AllArgsConstructor
public class MarketplaceProductService {

    private final CategoryRepository categoryRepository;
    private final MarketplaceProductRepository marketplaceProductRepository;
    private final MigrosProductDetailsService migrosProductDetailsService;
    private final YemeksepetiProductDetailsService yemeksepetiProductDetailsService;
    private final MarketplaceUrlProperties marketplaceUrlProperties;

    public AddProductResult addProduct(String marketplaceCode, String categoryName, String productId) {
        Marketplace marketplace = Marketplace.fromCode(marketplaceCode);
        if (marketplace == null) {
            return AddProductResult.badRequest(ResponseMessages.INVALID_MARKETPLACE_CODE);
        }

        Category category = categoryRepository.findByName(categoryName.trim())
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
}
