package com.mustafabulu.smartpantry.migros.service;

import com.mustafabulu.smartpantry.migros.model.MigrosProductDetails;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.model.Product;
import com.mustafabulu.smartpantry.config.MarketplaceUrlProperties;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.repository.PriceHistoryRepository;
import com.mustafabulu.smartpantry.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrosProductDetailsServiceTest {

    @Mock
    private MigrosScraperService scraperService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MarketplaceProductRepository marketplaceProductRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private MarketplaceUrlProperties marketplaceUrlProperties;

    private MigrosProductDetailsService service;

    @BeforeEach
    void setUp() {
        service = new MigrosProductDetailsService(
                scraperService,
                categoryRepository,
                marketplaceProductRepository,
                productRepository,
                priceHistoryRepository,
                marketplaceUrlProperties
        );
    }

    @Test
    void recordDailyDetailsSkipsBlankCategory() {
        service.recordDailyDetails(" ");

        verify(categoryRepository, never()).findByNameIgnoreCase(any());
    }

    @Test
    void recordDetailsForProductReturnsTrueWhenAlreadyRecorded() {
        Category category = new Category();
        MarketplaceProduct marketplaceProduct = new MarketplaceProduct();
        when(priceHistoryRepository.existsByMarketplaceProductAndRecordedAtBetween(any(), any(), any()))
                .thenReturn(true);

        boolean result = service.recordDetailsForProduct(category, marketplaceProduct);

        assertTrue(result);
        verify(scraperService, never()).fetchProductDetails(any());
    }

    @Test
    void recordDetailsForProductReturnsFalseWhenFetchFails() {
        Category category = new Category();
        MarketplaceProduct marketplaceProduct = new MarketplaceProduct();
        marketplaceProduct.setExternalId("1");
        marketplaceProduct.setProductUrl("https://example");
        when(priceHistoryRepository.existsByMarketplaceProductAndRecordedAtBetween(any(), any(), any()))
                .thenReturn(false);
        when(scraperService.fetchProductDetails("https://example")).thenReturn(null);

        boolean result = service.recordDetailsForProduct(category, marketplaceProduct);

        assertFalse(result);
    }

    @Test
    void recordDetailsForProductCreatesHistory() {
        Category category = new Category();
        category.setName("Snacks");
        MarketplaceProduct marketplaceProduct = new MarketplaceProduct();
        marketplaceProduct.setExternalId("1");
        marketplaceProduct.setProductUrl("https://example");
        when(priceHistoryRepository.existsByMarketplaceProductAndRecordedAtBetween(any(), any(), any()))
                .thenReturn(false);
        when(scraperService.fetchProductDetails("https://example"))
                .thenReturn(new MigrosProductDetails("Chips", 12.5, "g", 150, "Brand"));
        when(productRepository.findByNameIgnoreCaseAndCategory("Chips", category)).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = service.recordDetailsForProduct(category, marketplaceProduct);

        assertTrue(result);
        verify(priceHistoryRepository).save(any());
    }

    @Test
    void recordDailyDetailsProcessesMarketplaceProducts() {
        Category category = new Category();
        category.setName("Snacks");
        MarketplaceProduct marketplaceProduct = new MarketplaceProduct();
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.of(category));
        when(marketplaceProductRepository.findByMarketplaceAndCategory(any(), any()))
                .thenReturn(List.of(marketplaceProduct));
        when(priceHistoryRepository.existsByMarketplaceProductAndRecordedAtBetween(any(), any(), any()))
                .thenReturn(true);

        service.recordDailyDetails("Snacks");

        verify(priceHistoryRepository).existsByMarketplaceProductAndRecordedAtBetween(any(), any(), any());
    }
}
