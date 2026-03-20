package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketplaceCategorySeedResolverService {

    private final List<MarketplaceCategorySeedService> services;

    public List<String> listSeedCategoryKeys(Marketplace marketplace) {
        if (marketplace == null) {
            return List.of();
        }
        return services.stream()
                .filter(service -> service.marketplace() == marketplace)
                .findFirst()
                .map(MarketplaceCategorySeedService::listSeedCategoryKeys)
                .orElse(List.of());
    }
}
