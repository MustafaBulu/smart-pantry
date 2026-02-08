package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.dto.response.ProductResponse;
import com.mustafabulu.smartpantry.dto.request.ProductSearchRequest;
import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.model.Product;
import com.mustafabulu.smartpantry.repository.PriceHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductSearchService {

    private final PriceHistoryRepository priceHistoryRepository;

    public ProductSearchService(PriceHistoryRepository priceHistoryRepository) {
        this.priceHistoryRepository = priceHistoryRepository;
    }

    public List<ProductResponse> search(ProductSearchRequest request) {
        Marketplace marketplace = null;
        String marketplaceCode = request == null ? null : request.marketplaceCode();
        if (marketplaceCode != null && !marketplaceCode.isBlank()) {
            marketplace = Marketplace.fromCode(marketplaceCode);
        }

        String categoryName = request == null ? null : request.categoryName();
        String normalizedCategory = (categoryName == null || categoryName.isBlank())
                ? null
                : categoryName.trim();

        List<Product> products;
        if (marketplace != null && normalizedCategory != null) {
            products = priceHistoryRepository.findDistinctProductsByMarketplaceAndCategory(
                    marketplace,
                    normalizedCategory
            );
        } else if (marketplace != null) {
            products = priceHistoryRepository.findDistinctProductsByMarketplace(marketplace);
        } else if (normalizedCategory != null) {
            products = priceHistoryRepository.findDistinctProductsByCategory(normalizedCategory);
        } else {
            products = priceHistoryRepository.findDistinctProducts();
        }

        return products.stream()
                .map(product -> new ProductResponse(product.getId(), product.getName()))
                .toList();
    }
}

