package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketplaceCatalogUrlFetchResolverServiceTest {

    @Test
    void fetchAllByUrlDelegatesToMatchingService() {
        MarketplaceCatalogUrlFetchService service = mock(MarketplaceCatalogUrlFetchService.class);
        var result = List.of(new MarketplaceCatalogUrlFetchService.CatalogUrlProductCandidate("Sut", null));
        when(service.marketplace()).thenReturn(Marketplace.MG);
        when(service.fetchAllByUrl("url")).thenReturn(result);

        MarketplaceCatalogUrlFetchResolverService resolver = new MarketplaceCatalogUrlFetchResolverService(List.of(service));

        assertEquals(result, resolver.fetchAllByUrl(Marketplace.MG, "url"));
        assertTrue(resolver.fetchAllByUrl(null, "url").isEmpty());
    }
}
