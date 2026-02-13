package com.mustafabulu.smartpantry.service;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class MarketplaceCategorySearchService {

    private final List<MarketplaceCategoryFetchService> fetchServices;

    public MarketplaceCategorySearchService(List<MarketplaceCategoryFetchService> fetchServices) {
        this.fetchServices = fetchServices;
    }

    public List<MarketplaceCategoryFetchService.MarketplaceProductCandidate> fetchAll(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return List.of();
        }
        return fetchServices.stream()
                .flatMap(service -> service.fetchByCategory(categoryName.trim()).stream())
                .sorted(Comparator.comparing(candidate -> candidate.marketplace().name()))
                .toList();
    }
}
