package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.dto.response.BulkAddResponse;
import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.common.repository.CategoryRepository;
import com.mustafabulu.smartpantry.common.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.common.repository.PriceHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
    private MarketplaceProductConnectorRegistry connectorRegistry;

    @Mock
    private MarketplaceProductConnector ysConnector;

    @Mock
    private MarketplaceProductConnector mgConnector;

    @Mock
    private TaskExecutor taskExecutor;

    @Mock
    private MarketplaceCategorySearchService marketplaceCategorySearchService;

    @InjectMocks
    private MarketplaceProductService service;

    @BeforeEach
    void setUp() {
        lenient().when(connectorRegistry.get(Marketplace.YS)).thenReturn(Optional.of(ysConnector));
        lenient().when(connectorRegistry.get(Marketplace.MG)).thenReturn(Optional.of(mgConnector));
        lenient().when(ysConnector.buildProductUrl(any())).thenReturn("https://example/ys/1");
        lenient().when(mgConnector.buildProductUrl(any())).thenReturn("https://example/mg/1");
        lenient().when(marketplaceCategorySearchService.fetchByMarketplace(anyString(), any()))
                .thenReturn(List.of());
        lenient().doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    void addProductRejectsInvalidMarketplace() {
        MarketplaceProductService.AddProductResult result = service.addProduct("XX", "Snacks", "1");

        assertEquals(HttpStatus.BAD_REQUEST, result.status());
    }

    @Test
    void addProductReturnsNotFoundWhenCategoryMissing() {
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.empty());

        MarketplaceProductService.AddProductResult result = service.addProduct("YS", "Snacks", "1");

        assertEquals(HttpStatus.NOT_FOUND, result.status());
    }

    @Test
    void addProductReturnsOkWhenAlreadyExists() {
        Category category = new Category();
        category.setName("Snacks");
        MarketplaceProduct existing = new MarketplaceProduct();
        existing.setMarketplace(Marketplace.YS);
        existing.setExternalId("1");
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.of(category));
        when(marketplaceProductRepository.findByMarketplaceAndCategoryAndExternalId(Marketplace.YS, category, "1"))
                .thenReturn(Optional.of(existing));
        when(ysConnector.recordDetailsForProduct(category, existing)).thenReturn(true);

        MarketplaceProductService.AddProductResult result = service.addProduct("YS", "Snacks", "1");

        assertEquals(HttpStatus.OK, result.status());
        verify(marketplaceProductRepository, never()).delete(existing);
    }

    @Test
    void addProductReturnsOkWhenRecordFailsWithoutNewEntity() {
        Category category = new Category();
        category.setName("Snacks");
        MarketplaceProduct existing = new MarketplaceProduct();
        existing.setMarketplace(Marketplace.MG);
        existing.setExternalId("1");
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.of(category));
        when(marketplaceProductRepository.findByMarketplaceAndCategoryAndExternalId(Marketplace.MG, category, "1"))
                .thenReturn(Optional.of(existing));
        when(mgConnector.recordDetailsForProduct(category, existing)).thenReturn(false);

        MarketplaceProductService.AddProductResult result = service.addProduct("MG", "Snacks", "1");

        assertEquals(HttpStatus.OK, result.status());
        verify(marketplaceProductRepository, never()).delete(existing);
    }

    @Test
    void addProductReturnsBadRequestWhenDetailVerificationFailsForNewProduct() {
        Category category = new Category();
        category.setName("Snacks");
        MarketplaceProduct created = new MarketplaceProduct();
        created.setMarketplace(Marketplace.YS);
        created.setCategory(category);
        created.setExternalId("1");
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.of(category));
        when(marketplaceProductRepository.findByMarketplaceAndCategoryAndExternalId(Marketplace.YS, category, "1"))
                .thenReturn(Optional.empty());
        when(marketplaceProductRepository.save(any(MarketplaceProduct.class))).thenReturn(created);
        when(ysConnector.recordDetailsForProduct(category, created)).thenReturn(false);

        MarketplaceProductService.AddProductResult result = service.addProduct("YS", "Snacks", "1");

        assertEquals(HttpStatus.BAD_REQUEST, result.status());
        verify(marketplaceProductRepository).delete(created);
    }

    @Test
    void addProductsBulkKeepsCreatedProductWhenVerificationFailsButCandidateMetadataExists() {
        Category category = new Category();
        category.setName("Snacks");
        MarketplaceProduct created = new MarketplaceProduct();
        created.setMarketplace(Marketplace.YS);
        created.setCategory(category);
        created.setExternalId("1");
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.of(category));
        when(marketplaceProductRepository.findByMarketplaceAndCategoryAndExternalId(Marketplace.YS, category, "1"))
                .thenReturn(Optional.empty());
        when(marketplaceProductRepository.save(any(MarketplaceProduct.class))).thenReturn(created);
        when(ysConnector.recordDetailsForProduct(category, created)).thenReturn(false);
        when(marketplaceCategorySearchService.fetchByMarketplace("Snacks", Marketplace.YS))
                .thenReturn(List.of(new MarketplaceCategoryFetchService.MarketplaceProductCandidate(
                        Marketplace.YS,
                        "1",
                        "Sample",
                        "Brand",
                        "https://example/image.png",
                        new BigDecimal("10.00"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )));

        BulkAddResponse response = service.addProducts("YS", "Snacks", List.of("1"));

        assertEquals(1, response.created());
        assertEquals(0, response.failed());
        verify(marketplaceProductRepository, never()).delete(created);
    }

    @Test
    void addProductKeepsCreatedProductWhenVerificationFailsButCandidateMetadataExists() {
        Category category = new Category();
        category.setName("Snacks");
        MarketplaceProduct created = new MarketplaceProduct();
        created.setMarketplace(Marketplace.YS);
        created.setCategory(category);
        created.setExternalId("1");
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.of(category));
        when(marketplaceProductRepository.findByMarketplaceAndCategoryAndExternalId(Marketplace.YS, category, "1"))
                .thenReturn(Optional.empty());
        when(marketplaceProductRepository.save(any(MarketplaceProduct.class))).thenReturn(created);
        when(ysConnector.recordDetailsForProduct(category, created)).thenReturn(false);
        when(marketplaceCategorySearchService.fetchByMarketplace("Snacks", Marketplace.YS))
                .thenReturn(List.of(new MarketplaceCategoryFetchService.MarketplaceProductCandidate(
                        Marketplace.YS,
                        "1",
                        "Sample",
                        "Brand",
                        "https://example/image.png",
                        new BigDecimal("10.00"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )));

        MarketplaceProductService.AddProductResult result = service.addProduct("YS", "Snacks", "1");

        assertEquals(HttpStatus.CREATED, result.status());
        verify(marketplaceProductRepository, never()).delete(created);
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
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.empty());

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
        when(mgConnector.recordDetailsForProduct(category, product)).thenReturn(false);

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
        when(mgConnector.recordDetailsForProduct(category, product)).thenReturn(true);

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
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.empty());

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
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.of(category));

        MarketplaceProductService.DeleteMarketplaceProductResult result = service.deleteMarketplaceProduct("YS", "1", "Snacks");

        assertEquals(HttpStatus.NOT_FOUND, result.status());
    }

    @Test
    void addProductsBulkCountsFailuresForBlankIds() {
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.empty());

        var response = service.addProducts("YS", "Snacks", List.of(" ", "1"));

        assertTrue(response.failed() >= 1);
    }
}
