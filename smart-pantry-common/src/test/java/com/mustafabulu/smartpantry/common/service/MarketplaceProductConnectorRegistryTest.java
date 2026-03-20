package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketplaceProductConnectorRegistryTest {

    @Test
    void getReturnsRegisteredConnector() {
        MarketplaceProductConnector connector = mock(MarketplaceProductConnector.class);
        when(connector.marketplace()).thenReturn(Marketplace.MG);

        MarketplaceProductConnectorRegistry registry = new MarketplaceProductConnectorRegistry(List.of(connector));

        assertTrue(registry.get(Marketplace.MG).isPresent());
        assertTrue(registry.get(Marketplace.YS).isEmpty());
    }
}
