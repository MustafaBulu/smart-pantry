package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.config.MarketplaceUrlProperties;
import com.mustafabulu.smartpantry.core.exception.SPException;
import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.migros.service.MigrosProductDetailsService;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.repository.PriceHistoryRepository;
import com.mustafabulu.smartpantry.yemeksepeti.service.YemeksepetiProductDetailsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceProductServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MarketplaceProductRepository marketplaceProductRepository;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private MigrosProductDetailsService migrosProductDetailsService;

    @Mock
    private YemeksepetiProductDetailsService yemeksepetiProductDetailsService;

    @Mock
    private MarketplaceUrlProperties marketplaceUrlProperties;

    @InjectMocks
    private MarketplaceProductService service;

    @Test
    void addProductRejectsInvalidMarketplace() {
        MarketplaceProductService.AddProductResult result = service.addProduct("XX", "Snacks", "1");

        assertEquals(HttpStatus.BAD_REQUEST, result.status());
    }

    @Test
    void addProductReturnsNotFoundWhenCategoryMissing() {
        when(categoryRepository.findByName("Snacks")).thenReturn(Optional.empty());

        MarketplaceProductService.AddProductResult result = service.addProduct("YS", "Snacks", "1");

        assertEquals(HttpStatus.NOT_FOUND, result.status());
    }

    @Test
    void addProductReturnsOkWhenAlreadyExists() {
        Category category = new Category();
        category.setName("Snacks");
        MarketplaceProduct existing = new MarketplaceProduct();
        when(categoryRepository.findByName("Snacks")).thenReturn(Optional.of(category));
        when(marketplaceProductRepository.findByMarketplaceAndCategoryAndExternalId(Marketplace.YS, category, "1"))
                .thenReturn(Optional.of(existing));
        when(yemeksepetiProductDetailsService.recordDetailsForProduct(category, existing)).thenReturn(true);

        MarketplaceProductService.AddProductResult result = service.addProduct("YS", "Snacks", "1");

        assertEquals(HttpStatus.OK, result.status());
        verify(marketplaceProductRepository, never()).delete(existing);
    }

    @Test
    void addProductReturnsBadRequestWhenRecordFailsWithoutNewEntity() {
        Category category = new Category();
        category.setName("Snacks");
        MarketplaceProduct existing = new MarketplaceProduct();
        when(categoryRepository.findByName("Snacks")).thenReturn(Optional.of(category));
        when(marketplaceProductRepository.findByMarketplaceAndCategoryAndExternalId(Marketplace.MG, category, "1"))
                .thenReturn(Optional.of(existing));
        when(migrosProductDetailsService.recordDetailsForProduct(category, existing)).thenReturn(false);

        MarketplaceProductService.AddProductResult result = service.addProduct("MG", "Snacks", "1");

        assertEquals(HttpStatus.BAD_REQUEST, result.status());
        verify(marketplaceProductRepository, never()).delete(existing);
    }

    @Test
    void addProductDeletesNewEntityOnNotFoundException() {
        Category category = new Category();
        category.setName("Snacks");
        when(categoryRepository.findByName("Snacks")).thenReturn(Optional.of(category));
        when(marketplaceProductRepository.findByMarketplaceAndCategoryAndExternalId(Marketplace.YS, category, "1"))
                .thenReturn(Optional.empty());
        when(marketplaceUrlProperties.getYemeksepetiBase()).thenReturn("https://example/");
        when(marketplaceProductRepository.save(any(MarketplaceProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(yemeksepetiProductDetailsService.recordDetailsForProduct(any(), any()))
                .thenThrow(new SPException(HttpStatus.NOT_FOUND, "msg", "reason"));

        MarketplaceProductService.AddProductResult result = service.addProduct("YS", "Snacks", "1");

        assertEquals(HttpStatus.NOT_FOUND, result.status());
        verify(marketplaceProductRepository).delete(any(MarketplaceProduct.class));
    }

    @Test
    void refreshProductReturnsBadRequestWhenMarketplaceInvalid() {
        MarketplaceProductService.RefreshProductResult result = service.refreshProduct("XX", "1", null);

        assertEquals(HttpStatus.BAD_REQUEST, result.status());
    }

    @Test
    void refreshProductReturnsNotFoundWhenCategoryMissing() {
        when(marketplaceProductRepository.findByMarketplaceAndExternalId(Marketplace.YS, "1"))
                .thenReturn(List.of(new MarketplaceProduct()));
        when(categoryRepository.findByName("Snacks")).thenReturn(Optional.empty());

        MarketplaceProductService.RefreshProductResult result = service.refreshProduct("YS", "1", "Snacks");

        assertEquals(HttpStatus.NOT_FOUND, result.status());
    }

    @Test
    void refreshProductReturnsBadRequestWhenRecordFails() {
        Category category = new Category();
        category.setName("Snacks");
        MarketplaceProduct product = new MarketplaceProduct();
        product.setCategory(category);
        when(marketplaceProductRepository.findByMarketplaceAndExternalId(Marketplace.MG, "1"))
                .thenReturn(List.of(product));
        when(migrosProductDetailsService.recordDetailsForProduct(category, product)).thenReturn(false);

        MarketplaceProductService.RefreshProductResult result = service.refreshProduct("MG", "1", null);

        assertEquals(HttpStatus.BAD_REQUEST, result.status());
    }

    @Test
    void refreshProductReturnsConflictWhenAmbiguous() {
        MarketplaceProduct product1 = new MarketplaceProduct();
        MarketplaceProduct product2 = new MarketplaceProduct();
        when(marketplaceProductRepository.findByMarketplaceAndExternalId(Marketplace.YS, "1"))
                .thenReturn(List.of(product1, product2));

        MarketplaceProductService.RefreshProductResult result = service.refreshProduct("YS", "1", null);

        assertEquals(HttpStatus.CONFLICT, result.status());
    }

    @Test
    void refreshProductReturnsOkWhenRecorded() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Snacks");
        MarketplaceProduct product = new MarketplaceProduct();
        product.setCategory(category);
        when(marketplaceProductRepository.findByMarketplaceAndExternalId(Marketplace.MG, "1"))
                .thenReturn(List.of(product));
        when(migrosProductDetailsService.recordDetailsForProduct(category, product)).thenReturn(true);

        MarketplaceProductService.RefreshProductResult result = service.refreshProduct("MG", "1", null);

        assertEquals(HttpStatus.OK, result.status());
    }

    @Test
    void deleteMarketplaceProductRemovesRecords() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Snacks");
        MarketplaceProduct product = new MarketplaceProduct();
        product.setId(7L);
        product.setCategory(category);
        when(marketplaceProductRepository.findByMarketplaceAndExternalId(Marketplace.YS, "1"))
                .thenReturn(List.of(product));

        MarketplaceProductService.DeleteMarketplaceProductResult result = service.deleteMarketplaceProduct("YS", "1", null);

        assertEquals(HttpStatus.OK, result.status());
        verify(priceHistoryRepository).deleteByMarketplaceProductId(7L);
        verify(marketplaceProductRepository).delete(product);
    }

    @Test
    void deleteMarketplaceProductReturnsBadRequestWhenMarketplaceInvalid() {
        MarketplaceProductService.DeleteMarketplaceProductResult result = service.deleteMarketplaceProduct("XX", "1", null);

        assertEquals(HttpStatus.BAD_REQUEST, result.status());
    }

    @Test
    void deleteMarketplaceProductReturnsNotFoundWhenCategoryMissing() {
        when(marketplaceProductRepository.findByMarketplaceAndExternalId(Marketplace.YS, "1"))
                .thenReturn(List.of(new MarketplaceProduct()));
        when(categoryRepository.findByName("Snacks")).thenReturn(Optional.empty());

        MarketplaceProductService.DeleteMarketplaceProductResult result = service.deleteMarketplaceProduct("YS", "1", "Snacks");

        assertEquals(HttpStatus.NOT_FOUND, result.status());
    }

    @Test
    void deleteMarketplaceProductReturnsNotFoundWhenNoMatchesAfterFilter() {
        Category category = new Category();
        category.setName("Snacks");
        MarketplaceProduct product = new MarketplaceProduct();
        Category other = new Category();
        other.setName("Other");
        product.setCategory(other);
        when(marketplaceProductRepository.findByMarketplaceAndExternalId(Marketplace.YS, "1"))
                .thenReturn(List.of(product));
        when(categoryRepository.findByName("Snacks")).thenReturn(Optional.of(category));

        MarketplaceProductService.DeleteMarketplaceProductResult result = service.deleteMarketplaceProduct("YS", "1", "Snacks");

        assertEquals(HttpStatus.NOT_FOUND, result.status());
    }

    @Test
    void addProductsBulkCountsFailuresForBlankIds() {
        when(categoryRepository.findByName("Snacks")).thenReturn(Optional.empty());

        var response = service.addProducts("YS", "Snacks", List.of(" ", "1"));

        assertTrue(response.failed() >= 1);
    }
}
