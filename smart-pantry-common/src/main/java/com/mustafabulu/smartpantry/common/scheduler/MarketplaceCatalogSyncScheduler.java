package com.mustafabulu.smartpantry.common.scheduler;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.service.MarketplaceProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketplaceCatalogSyncScheduler {

    private final MarketplaceProductService marketplaceProductService;
    private final boolean migrosSchedulerEnabled;
    private final boolean yemeksepetiSchedulerEnabled;

    public MarketplaceCatalogSyncScheduler(
            MarketplaceProductService marketplaceProductService,
            @Value("${catalog.sync.scheduler.migros.enabled:true}") boolean migrosSchedulerEnabled,
            @Value("${catalog.sync.scheduler.yemeksepeti.enabled:true}") boolean yemeksepetiSchedulerEnabled
    ) {
        this.marketplaceProductService = marketplaceProductService;
        this.migrosSchedulerEnabled = migrosSchedulerEnabled;
        this.yemeksepetiSchedulerEnabled = yemeksepetiSchedulerEnabled;
    }

    @Scheduled(
            fixedDelayString = "${catalog.sync.scheduler.migros.fixed-delay:PT2H}",
            initialDelayString = "${catalog.sync.scheduler.migros.initial-delay:PT1M}"
    )
    public void syncMigrosCatalog() {
        if (!migrosSchedulerEnabled) {
            return;
        }
        marketplaceProductService.syncCatalogForMarketplace(Marketplace.MG);
    }

    @Scheduled(
            fixedDelayString = "${catalog.sync.scheduler.yemeksepeti.fixed-delay:PT2H}",
            initialDelayString = "${catalog.sync.scheduler.yemeksepeti.initial-delay:PT6M}"
    )
    public void syncYemeksepetiCatalog() {
        if (!yemeksepetiSchedulerEnabled) {
            return;
        }
        marketplaceProductService.syncCatalogForMarketplace(Marketplace.YS);
    }
}
