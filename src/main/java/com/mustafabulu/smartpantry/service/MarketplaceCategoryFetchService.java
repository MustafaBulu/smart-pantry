package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.enums.Marketplace;

import java.util.List;

public interface MarketplaceCategoryFetchService {

    Marketplace marketplace();

    List<MarketplaceProductCandidate> fetchByCategory(String categoryName);

    record MarketplaceProductCandidate(
            Marketplace marketplace,
            String externalId,
            String name,
            String brandName,
            String imageUrl,
            java.math.BigDecimal price
    ) {
    }
}
