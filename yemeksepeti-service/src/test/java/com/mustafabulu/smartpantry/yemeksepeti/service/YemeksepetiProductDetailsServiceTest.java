package com.mustafabulu.smartpantry.yemeksepeti.service;

import com.mustafabulu.smartpantry.common.config.MarketplaceUrlProperties;
import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.common.model.Product;
import com.mustafabulu.smartpantry.common.repository.CategoryRepository;
import com.mustafabulu.smartpantry.common.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.common.repository.PriceHistoryRepository;
import com.mustafabulu.smartpantry.common.repository.ProductRepository;
import com.mustafabulu.smartpantry.yemeksepeti.model.YemeksepetiProductDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
class YemeksepetiProductDetailsServiceTest {

    @Mock
    private YemeksepetiScraperService scraperService;

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

    @InjectMocks
    private YemeksepetiProductDetailsService service;

    @Test
    void recordDailyDetailsSkipsBlankCategory() {
        service.recordDailyDetails(" ");

        verify(categoryRepository, never()).findByNameIgnoreCase(any());
    }

    @Test
    void recordDailyDetailsCreatesCategoryWhenMissing() {
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(marketplaceProductRepository.findByMarketplaceAndCategory(any(), any())).thenReturn(List.of());

        service.recordDailyDetails("Snacks");

        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void recordDetailsForProductReturnsTrueWhenAlreadyRecorded() {
        Category category = new Category();
        MarketplaceProduct marketplaceProduct = new MarketplaceProduct();
        marketplaceProduct.setProductUrl("https://example");
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
        marketplaceProduct.setProductUrl("https://example");
        when(priceHistoryRepository.existsByMarketplaceProductAndRecordedAtBetween(any(), any(), any()))
                .thenReturn(false);
        when(scraperService.fetchProductDetails("https://example")).thenReturn(null);
        when(marketplaceUrlProperties.getYemeksepetiBase()).thenReturn("https://example");

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
                .thenReturn(new YemeksepetiProductDetails("Chips", 10, 0, 1, "", "", 1, "g", 100));
        when(productRepository.findByNameIgnoreCaseAndCategory("Chips", category)).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = service.recordDetailsForProduct(category, marketplaceProduct);

        assertTrue(result);
        verify(priceHistoryRepository).save(any());
    }
}
