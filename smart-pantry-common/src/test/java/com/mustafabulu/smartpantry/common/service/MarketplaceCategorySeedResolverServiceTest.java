package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketplaceCategorySeedResolverServiceTest {

    @Test
    void listSeedCategoryKeysDelegatesToMatchingService() {
        MarketplaceCategorySeedService service = mock(MarketplaceCategorySeedService.class);
        when(service.marketplace()).thenReturn(Marketplace.YS);
        when(service.listSeedCategoryKeys()).thenReturn(List.of("Meyve", "Icecek"));

        MarketplaceCategorySeedResolverService resolver = new MarketplaceCategorySeedResolverService(List.of(service));

        assertEquals(List.of("Meyve", "Icecek"), resolver.listSeedCategoryKeys(Marketplace.YS));
        assertTrue(resolver.listSeedCategoryKeys(null).isEmpty());
    }
}
