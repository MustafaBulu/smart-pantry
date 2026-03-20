package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;

public interface MarketplaceCatalogCategoryRangeImportService {

    Marketplace marketplace();

    CatalogCategoryRangeImportResult importFromCategoryRange(
            String sourceUrl,
            int startCategoryId,
            int endCategoryId
    );

    record CatalogCategoryRangeImportResult(
            String marketplaceCode,
            int categoryCount,
            int totalPageCount,
            int totalCollectedProductCount,
            int uniqueProductCount,
            int createdCount,
            int updatedCount
    ) {
    }
}
