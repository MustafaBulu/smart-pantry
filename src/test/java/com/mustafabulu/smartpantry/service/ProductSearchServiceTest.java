package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.dto.request.ProductSearchRequest;
import com.mustafabulu.smartpantry.dto.response.ProductResponse;
import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.model.Product;
import com.mustafabulu.smartpantry.repository.PriceHistoryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSearchServiceTest {

    @Test
    void searchWithMarketplaceAndCategoryUsesSpecificQuery() {
        PriceHistoryRepository repository = mock(PriceHistoryRepository.class);
        ProductSearchService service = new ProductSearchService(repository);
        Product product = new Product();
        product.setId(1L);
        product.setName("Chips");
        when(repository.findDistinctProductsByMarketplaceAndCategory(Marketplace.YS, "Snacks"))
                .thenReturn(List.of(product));

        List<ProductResponse> responses = service.search(new ProductSearchRequest("YS", "Snacks"));

        assertEquals(1, responses.size());
        assertEquals("Chips", responses.getFirst().name());
        verify(repository).findDistinctProductsByMarketplaceAndCategory(Marketplace.YS, "Snacks");
    }

    @Test
    void searchWithCategoryOnlyUsesCategoryQuery() {
        PriceHistoryRepository repository = mock(PriceHistoryRepository.class);
        ProductSearchService service = new ProductSearchService(repository);
        when(repository.findDistinctProductsByCategory("Snacks")).thenReturn(List.of());

        service.search(new ProductSearchRequest(null, "Snacks"));

        verify(repository).findDistinctProductsByCategory("Snacks");
    }

    @Test
    void searchWithMarketplaceOnlyUsesMarketplaceQuery() {
        PriceHistoryRepository repository = mock(PriceHistoryRepository.class);
        ProductSearchService service = new ProductSearchService(repository);
        when(repository.findDistinctProductsByMarketplace(Marketplace.MG)).thenReturn(List.of());

        service.search(new ProductSearchRequest("MG", null));

        verify(repository).findDistinctProductsByMarketplace(Marketplace.MG);
    }

    @Test
    void searchWithNoFiltersUsesAllProductsQuery() {
        PriceHistoryRepository repository = mock(PriceHistoryRepository.class);
        ProductSearchService service = new ProductSearchService(repository);
        when(repository.findDistinctProducts()).thenReturn(List.of());

        service.search(new ProductSearchRequest(null, null));

        verify(repository).findDistinctProducts();
    }

    @Test
    void searchWithInvalidMarketplaceFallsBackToCategory() {
        PriceHistoryRepository repository = mock(PriceHistoryRepository.class);
        ProductSearchService service = new ProductSearchService(repository);
        when(repository.findDistinctProductsByCategory("Snacks")).thenReturn(List.of());

        service.search(new ProductSearchRequest("XX", "Snacks"));

        verify(repository).findDistinctProductsByCategory("Snacks");
    }
}
