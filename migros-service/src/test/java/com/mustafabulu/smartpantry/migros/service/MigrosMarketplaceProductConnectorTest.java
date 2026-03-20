package com.mustafabulu.smartpantry.migros.service;

import com.mustafabulu.smartpantry.common.config.MarketplaceUrlProperties;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.model.MarketplaceProduct;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MigrosMarketplaceProductConnectorTest {

    @Test
    void connectorBuildsUrlAndDelegatesDetailsRecording() {
        MigrosProductDetailsService detailsService = mock(MigrosProductDetailsService.class);
        MarketplaceUrlProperties properties = new MarketplaceUrlProperties();
        properties.setMigrosPrefix("https://mg/");
        properties.setMigrosSuffix("/detail");
        Category category = new Category();
        MarketplaceProduct product = new MarketplaceProduct();
        when(detailsService.recordDetailsForProduct(category, product)).thenReturn(true);

        MigrosMarketplaceProductConnector connector = new MigrosMarketplaceProductConnector(detailsService, properties);

        assertEquals(Marketplace.MG, connector.marketplace());
        assertEquals("https://mg/123/detail", connector.buildProductUrl("123"));
        assertTrue(connector.recordDetailsForProduct(category, product));
    }
}
