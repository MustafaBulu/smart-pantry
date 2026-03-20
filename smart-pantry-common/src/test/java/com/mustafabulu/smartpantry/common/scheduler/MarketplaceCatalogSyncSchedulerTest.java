package com.mustafabulu.smartpantry.common.scheduler;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.service.MarketplaceProductService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MarketplaceCatalogSyncSchedulerTest {

    @Test
    void syncMigrosCatalogRunsOnlyWhenEnabled() {
        MarketplaceProductService service = mock(MarketplaceProductService.class);
        MarketplaceCatalogSyncScheduler scheduler = new MarketplaceCatalogSyncScheduler(service, true, false);

        scheduler.syncMigrosCatalog();
        scheduler.syncYemeksepetiCatalog();

        verify(service).syncCatalogForMarketplace(Marketplace.MG);
        verify(service, never()).syncCatalogForMarketplace(Marketplace.YS);
    }
}
