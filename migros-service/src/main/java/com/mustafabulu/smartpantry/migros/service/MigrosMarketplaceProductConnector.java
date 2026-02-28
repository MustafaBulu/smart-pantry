package com.mustafabulu.smartpantry.migros.service;

import com.mustafabulu.smartpantry.common.config.MarketplaceUrlProperties;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.common.service.MarketplaceProductConnector;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "marketplace.mg", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigrosMarketplaceProductConnector implements MarketplaceProductConnector {

    private final MigrosProductDetailsService migrosProductDetailsService;
    private final MarketplaceUrlProperties marketplaceUrlProperties;

    @Override
    public Marketplace marketplace() {
        return Marketplace.MG;
    }

    @Override
    public String buildProductUrl(String externalId) {
        return marketplaceUrlProperties.getMigrosPrefix()
                + externalId
                + marketplaceUrlProperties.getMigrosSuffix();
    }

    @Override
    public boolean recordDetailsForProduct(Category category, MarketplaceProduct marketplaceProduct) {
        return migrosProductDetailsService.recordDetailsForProduct(category, marketplaceProduct);
    }
}
