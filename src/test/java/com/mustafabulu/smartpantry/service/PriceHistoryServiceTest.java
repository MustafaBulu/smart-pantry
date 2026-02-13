package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.core.exception.SPException;
import com.mustafabulu.smartpantry.dto.response.CategoryPriceSummaryResponse;
import com.mustafabulu.smartpantry.dto.response.PriceHistoryResponse;
import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.model.PriceHistory;
import com.mustafabulu.smartpantry.model.Product;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.repository.PriceHistoryRepository;
import com.mustafabulu.smartpantry.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceHistoryServiceTest {

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private PriceHistoryService priceHistoryService;

    @Test
    void getProductPricesDefaultsStartDate() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Chips");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(priceHistoryRepository.findByProductIdAndFilters(org.mockito.Mockito.eq(1L), org.mockito.Mockito.isNull(),
                org.mockito.Mockito.any(LocalDateTime.class), org.mockito.Mockito.isNull()))
                .thenReturn(List.of());

        priceHistoryService.getProductPrices(1L, null, null, null);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(priceHistoryRepository).findByProductIdAndFilters(org.mockito.Mockito.eq(1L), org.mockito.Mockito.isNull(),
                captor.capture(), org.mockito.Mockito.isNull());
        LocalDateTime captured = captor.getValue();
        LocalDateTime now = LocalDateTime.now();
        assertTrue(captured.isBefore(now.plusSeconds(1)));
    }

    @Test
    void getProductPricesMapsResponses() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Chips");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        PriceHistory history = new PriceHistory();
        history.setId(5L);
        history.setMarketplace(Marketplace.MG);
        history.setPrice(BigDecimal.valueOf(12.5));
        history.setRecordedAt(LocalDateTime.of(2026, 2, 1, 10, 0));
        when(priceHistoryRepository.findByProductIdAndFilters(org.mockito.Mockito.eq(1L), org.mockito.Mockito.isNull(),
                org.mockito.Mockito.any(LocalDateTime.class), org.mockito.Mockito.isNull()))
                .thenReturn(List.of(history));

        List<PriceHistoryResponse> responses = priceHistoryService.getProductPrices(1L, null, LocalDateTime.now().minusYears(1), null);

        assertEquals(1, responses.size());
        assertEquals(LocalDate.of(2026, 2, 1), responses.getFirst().recordedAt());
    }

    @Test
    void getCategorySummaryDefaultsStartDateAndComputesStats() {
        Category category = new Category();
        category.setId(2L);
        category.setName("Snacks");
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.of(category));
        Product product = new Product();
        product.setId(3L);
        product.setName("Chips");
        PriceHistory first = new PriceHistory();
        first.setProduct(product);
        first.setPrice(BigDecimal.valueOf(10));
        first.setRecordedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        PriceHistory last = new PriceHistory();
        last.setProduct(product);
        last.setPrice(BigDecimal.valueOf(20));
        last.setRecordedAt(LocalDateTime.of(2026, 2, 1, 10, 0));
        when(priceHistoryRepository.findByCategoryNameAndFilters(org.mockito.Mockito.eq("Snacks"), org.mockito.Mockito.isNull(),
                org.mockito.Mockito.any(LocalDateTime.class), org.mockito.Mockito.isNull()))
                .thenReturn(List.of(last, first));

        List<CategoryPriceSummaryResponse> responses = priceHistoryService.getCategorySummary("Snacks", null, null, null);

        assertEquals(1, responses.size());
        CategoryPriceSummaryResponse response = responses.getFirst();
        assertEquals(BigDecimal.valueOf(10), response.minPrice());
        assertEquals(BigDecimal.valueOf(20), response.maxPrice());
        assertEquals(new BigDecimal("15.00"), response.avgPrice());
        assertEquals(LocalDate.of(2026, 2, 1), response.lastRecordedAt());
    }

    @Test
    void getCategorySummaryThrowsWhenMissingCategory() {
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.empty());

        assertThrows(SPException.class, () -> priceHistoryService.getCategorySummary("Snacks", null, null, null));
    }

    @Test
    void getProductPricesThrowsWhenMarketplaceInvalid() {
        Product product = new Product();
        product.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(SPException.class, () -> priceHistoryService.getProductPrices(1L, "XX", null, null));
    }

    @Test
    void getCategorySummaryThrowsWhenMarketplaceInvalid() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Snacks");
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.of(category));

        assertThrows(SPException.class, () -> priceHistoryService.getCategorySummary("Snacks", "XX", null, null));
    }
}
