package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Component
public class MarketplaceRequestResolver {

    private final boolean migrosEnabled;
    private final boolean yemeksepetiEnabled;

    public MarketplaceRequestResolver(
            @Value("${marketplace.mg.enabled:true}") boolean migrosEnabled,
            @Value("${marketplace.ys.enabled:true}") boolean yemeksepetiEnabled
    ) {
        this.migrosEnabled = migrosEnabled;
        this.yemeksepetiEnabled = yemeksepetiEnabled;
    }

    public String resolveOptional(String marketplaceCode) {
        if (marketplaceCode != null && !marketplaceCode.isBlank()) {
            Marketplace marketplace = Marketplace.fromCode(marketplaceCode);
            if (marketplace == null || !isEnabled(marketplace)) {
                throw new SPException(
                        BAD_REQUEST,
                        "Marketplace bu microservice'te aktif degil.",
                        "invalid_marketplace"
                );
            }
            return marketplace.getCode();
        }
        if (migrosEnabled ^ yemeksepetiEnabled) {
            return migrosEnabled ? "MG" : "YS";
        }
        return null;
    }

    public String resolveRequired(String marketplaceCode) {
        String resolved = resolveOptional(marketplaceCode);
        if (resolved != null) {
            return resolved;
        }
        throw new SPException(
                BAD_REQUEST,
                "marketplaceCode zorunlu.",
                "invalid_marketplace"
        );
    }

    private boolean isEnabled(Marketplace marketplace) {
        return switch (marketplace) {
            case MG -> migrosEnabled;
            case YS -> yemeksepetiEnabled;
        };
    }
}
