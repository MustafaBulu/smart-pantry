package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketplaceCatalogCategoryRangeImportResolverService {

    private final List<MarketplaceCatalogCategoryRangeImportService> services;

    public MarketplaceCatalogCategoryRangeImportService.CatalogCategoryRangeImportResult importFromCategoryRange(
            Marketplace marketplace,
            String sourceUrl,
            int startCategoryId,
            int endCategoryId
    ) {
        if (marketplace == null) {
            return new MarketplaceCatalogCategoryRangeImportService.CatalogCategoryRangeImportResult(
                    "",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }
        return services.stream()
                .filter(service -> service.marketplace() == marketplace)
                .findFirst()
                .map(service -> service.importFromCategoryRange(sourceUrl, startCategoryId, endCategoryId))
                .orElse(new MarketplaceCatalogCategoryRangeImportService.CatalogCategoryRangeImportResult(
                        marketplace.getCode(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0
                ));
    }
}
