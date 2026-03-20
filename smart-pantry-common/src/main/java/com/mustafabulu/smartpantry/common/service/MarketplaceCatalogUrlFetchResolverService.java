package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketplaceCatalogUrlFetchResolverService {

    private final List<MarketplaceCatalogUrlFetchService> services;

    public List<MarketplaceCatalogUrlFetchService.CatalogUrlProductCandidate> fetchAllByUrl(
            Marketplace marketplace,
            String sourceUrl
    ) {
        if (marketplace == null || sourceUrl == null || sourceUrl.isBlank()) {
            return List.of();
        }
        return services.stream()
                .filter(service -> service.marketplace() == marketplace)
                .findFirst()
                .map(service -> service.fetchAllByUrl(sourceUrl))
                .orElse(List.of());
    }
}
