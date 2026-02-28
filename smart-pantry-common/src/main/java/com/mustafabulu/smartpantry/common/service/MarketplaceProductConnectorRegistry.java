package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MarketplaceProductConnectorRegistry {

    private final Map<Marketplace, MarketplaceProductConnector> connectorsByMarketplace;

    public MarketplaceProductConnectorRegistry(List<MarketplaceProductConnector> connectors) {
        Map<Marketplace, MarketplaceProductConnector> map = new EnumMap<>(Marketplace.class);
        for (MarketplaceProductConnector connector : connectors) {
            map.put(connector.marketplace(), connector);
        }
        this.connectorsByMarketplace = Map.copyOf(map);
    }

    public Optional<MarketplaceProductConnector> get(Marketplace marketplace) {
        return Optional.ofNullable(connectorsByMarketplace.get(marketplace));
    }
}
