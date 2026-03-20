package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceCategorySearchServiceTest {

    @Test
    void fetchAllReturnsSortedMarketplaceCandidates() {
        MarketplaceCategoryFetchService ysService = new FixedFetchService(
                Marketplace.YS,
                List.of(candidate(Marketplace.YS, "ys-1"))
        );
        MarketplaceCategoryFetchService mgService = new FixedFetchService(
                Marketplace.MG,
                List.of(candidate(Marketplace.MG, "mg-1"))
        );
        MarketplaceCategorySearchService service = new MarketplaceCategorySearchService(List.of(ysService, mgService));

        List<MarketplaceCategoryFetchService.MarketplaceProductCandidate> result = service.fetchAll("sut");

        assertEquals(2, result.size());
        assertEquals("mg-1", result.getFirst().externalId());
        assertEquals("ys-1", result.get(1).externalId());
    }

    @Test
    void fetchAllReturnsEmptyForBlankCategory() {
        MarketplaceCategorySearchService service = new MarketplaceCategorySearchService(List.of());

        assertTrue(service.fetchAll(" ").isEmpty());
    }

    @Test
    void fetchByMarketplaceFiltersRequestedServiceOnly() {
        MarketplaceCategoryFetchService ysService = new FixedFetchService(
                Marketplace.YS,
                List.of(candidate(Marketplace.YS, "ys-1"))
        );
        MarketplaceCategoryFetchService mgService = new FixedFetchService(
                Marketplace.MG,
                List.of(candidate(Marketplace.MG, "mg-1"))
        );
        MarketplaceCategorySearchService service = new MarketplaceCategorySearchService(List.of(ysService, mgService));

        List<MarketplaceCategoryFetchService.MarketplaceProductCandidate> result =
                service.fetchByMarketplace("sut", Marketplace.YS);

        assertEquals(1, result.size());
        assertEquals("ys-1", result.getFirst().externalId());
    }

    private MarketplaceCategoryFetchService.MarketplaceProductCandidate candidate(
            Marketplace marketplace,
            String externalId
    ) {
        return new MarketplaceCategoryFetchService.MarketplaceProductCandidate(
                marketplace,
                externalId,
                "Sample",
                "Brand",
                "https://cdn.example.com/sample.jpg",
                new BigDecimal("10.00"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private record FixedFetchService(
            Marketplace marketplace,
            List<MarketplaceCategoryFetchService.MarketplaceProductCandidate> candidates
    ) implements MarketplaceCategoryFetchService {
        @Override
        public List<MarketplaceProductCandidate> fetchByCategory(String categoryName) {
            return candidates;
        }
    }
}
