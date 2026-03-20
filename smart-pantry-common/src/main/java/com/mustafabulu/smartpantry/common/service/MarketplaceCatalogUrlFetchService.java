package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;

import java.util.List;

public interface MarketplaceCatalogUrlFetchService {

    Marketplace marketplace();

    List<CatalogUrlProductCandidate> fetchAllByUrl(String sourceUrl);

    record CatalogUrlProductCandidate(
            String categoryName,
            MarketplaceCategoryFetchService.MarketplaceProductCandidate candidate
    ) {
    }
}
