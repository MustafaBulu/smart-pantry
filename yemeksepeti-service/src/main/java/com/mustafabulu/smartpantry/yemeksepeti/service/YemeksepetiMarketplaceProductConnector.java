package com.mustafabulu.smartpantry.yemeksepeti.service;

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
@ConditionalOnProperty(prefix = "marketplace.ys", name = "enabled", havingValue = "true", matchIfMissing = true)
public class YemeksepetiMarketplaceProductConnector implements MarketplaceProductConnector {

    private final YemeksepetiProductDetailsService yemeksepetiProductDetailsService;
    private final MarketplaceUrlProperties marketplaceUrlProperties;

    @Override
    public Marketplace marketplace() {
        return Marketplace.YS;
    }

    @Override
    public String buildProductUrl(String externalId) {
        return marketplaceUrlProperties.getYemeksepetiBase() + externalId;
    }

    @Override
    public boolean recordDetailsForProduct(Category category, MarketplaceProduct marketplaceProduct) {
        return yemeksepetiProductDetailsService.recordDetailsForProduct(category, marketplaceProduct);
    }
}
