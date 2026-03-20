package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketplaceCatalogCategoryRangeImportResolverServiceTest {

    @Test
    void importFromCategoryRangeDelegatesToMatchingService() {
        MarketplaceCatalogCategoryRangeImportService service = mock(MarketplaceCatalogCategoryRangeImportService.class);
        var result = new MarketplaceCatalogCategoryRangeImportService.CatalogCategoryRangeImportResult("MG", 1, 2, 3, 4, 5, 6);
        when(service.marketplace()).thenReturn(Marketplace.MG);
        when(service.importFromCategoryRange("url", 2, 4)).thenReturn(result);

        MarketplaceCatalogCategoryRangeImportResolverService resolver =
                new MarketplaceCatalogCategoryRangeImportResolverService(List.of(service));

        assertEquals(result, resolver.importFromCategoryRange(Marketplace.MG, "url", 2, 4));
        assertEquals("", resolver.importFromCategoryRange(null, "url", 2, 4).marketplaceCode());
    }
}
