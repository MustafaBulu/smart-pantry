package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.response.CategoryResponse;
import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductCandidateResponse;
import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductEntryResponse;
import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductMatchPairResponse;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.model.MarketplaceManualMatch;
import com.mustafabulu.smartpantry.common.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.common.model.PriceHistory;
import com.mustafabulu.smartpantry.common.model.Product;
import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductMatchScoreResponse;
import com.mustafabulu.smartpantry.common.repository.CategoryRepository;
import com.mustafabulu.smartpantry.common.repository.MarketplaceManualMatchRepository;
import com.mustafabulu.smartpantry.common.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.common.repository.PriceHistoryRepository;
import com.mustafabulu.smartpantry.common.repository.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    @SuppressWarnings("unused")
    private ProductRepository productRepository;

    @Mock
    @SuppressWarnings("unused")
    private MarketplaceProductRepository marketplaceProductRepository;

    @Mock
    @SuppressWarnings("unused")
    private MarketplaceManualMatchRepository marketplaceManualMatchRepository;

    @Mock
    @SuppressWarnings("unused")
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    @SuppressWarnings("unused")
    private MarketplaceCategorySearchService marketplaceCategorySearchService;

    @Mock
    @SuppressWarnings("unused")
    private CrossPlatformProductMatcherService crossPlatformProductMatcherService;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createCategoryThrowsWhenAlreadyExists() {
        Category category = new Category();
        category.setId(10L);
        category.setName("Snacks");
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.of(category));

        assertThrows(SPException.class, () -> categoryService.createCategory("Snacks", null));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void createCategoryCreatesWhenMissing() {
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.empty());
        when(categoryRepository.save(org.mockito.Mockito.any(Category.class)))
                .thenAnswer(invocation -> {
                    Category saved = invocation.getArgument(0);
                    saved.setId(5L);
                    return saved;
                });

        CategoryResponse response = categoryService.createCategory("Snacks", null);

        assertEquals(5L, response.id());
        assertEquals("Snacks", response.name());
    }

    @Test
    void createCategoryNormalizesMainCategory() {
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.empty());
        when(categoryRepository.save(org.mockito.Mockito.any(Category.class)))
                .thenAnswer(invocation -> {
                    Category saved = invocation.getArgument(0);
                    saved.setId(6L);
                    return saved;
                });

        CategoryResponse response = categoryService.createCategory("Snacks", "  Temel Gida  ");

        assertEquals(6L, response.id());
        assertEquals("Temel Gida", response.mainCategory());
    }

    @Test
    void createCategoryThrowsWhenBlank() {
        assertThrows(SPException.class, () -> categoryService.createCategory(" ", null));
    }

    @Test
    void createCategoryThrowsWhenNull() {
        assertThrows(SPException.class, () -> categoryService.createCategory(null, null));
    }

    @Test
    void updateCategoryThrowsWhenMissing() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(SPException.class, () -> categoryService.updateCategory(1L, "Snacks", null));
    }

    @Test
    void updateCategoryThrowsWhenIdNull() {
        assertThrows(SPException.class, () -> categoryService.updateCategory(null, "Snacks", null));
    }

    @Test
    void updateCategoryThrowsWhenNameBlank() {
        assertThrows(SPException.class, () -> categoryService.updateCategory(1L, " ", null));
    }

    @Test
    void updateCategoryRejectsDuplicateName() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Old");
        Category duplicate = new Category();
        duplicate.setId(2L);
        duplicate.setName("New");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCase("New")).thenReturn(Optional.of(duplicate));

        assertThrows(SPException.class, () -> categoryService.updateCategory(1L, "New", null));
    }

    @Test
    void updateCategoryUpdatesName() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Old");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCase("New")).thenReturn(Optional.empty());
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryResponse response = categoryService.updateCategory(1L, "New", null);

        assertEquals("New", response.name());
    }

    @Test
    void updateCategoryCanClearMainCategory() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Old");
        category.setMainCategory("Temel Gida");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCase("Old")).thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryResponse response = categoryService.updateCategory(1L, "Old", "   ");

        assertEquals("Old", response.name());
        assertNull(response.mainCategory());
    }

    @Test
    void deleteCategoryDeletesWhenInUse() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Snacks");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        assertDoesNotThrow(() -> categoryService.deleteCategory(1L));
        verify(categoryRepository).delete(category);
    }

    @Test
    void deleteCategoryThrowsWhenMissing() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(SPException.class, () -> categoryService.deleteCategory(1L));
    }

    @Test
    void deleteCategoryDeletesWhenFree() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Snacks");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        categoryService.deleteCategory(1L);

        verify(categoryRepository).delete(category);
    }

    @Test
    void listCategoriesMapsResponses() {
        Category category = new Category();
        category.setId(2L);
        category.setName("Snacks");
        category.setMainCategory("Temel Gida");
        when(categoryRepository.findAll()).thenReturn(List.of(category));

        List<CategoryResponse> responses = categoryService.listCategories();

        assertEquals(1, responses.size());
        assertEquals("Snacks", responses.getFirst().name());
        assertEquals("Temel Gida", responses.getFirst().mainCategory());
    }

    @Test
    void matchMarketplaceProductsDelegatesToMatcher() {
        MarketplaceProductCandidateResponse ys = new MarketplaceProductCandidateResponse(
                "YS", "ys1", "Sut", "Brand", "img", null, null, null, null, null, null, null, null, null, null
        );
        MarketplaceProductCandidateResponse mg = new MarketplaceProductCandidateResponse(
                "MG", "mg1", "Sut", "Brand", "img", null, null, null, null, null, null, null, null, null, null
        );
        MarketplaceProductMatchPairResponse pair = org.mockito.Mockito.mock(MarketplaceProductMatchPairResponse.class);
        when(crossPlatformProductMatcherService.buildMarketplacePairs(List.of(ys), List.of(mg), 0.76d))
                .thenReturn(List.of(pair));

        List<MarketplaceProductMatchPairResponse> response =
                categoryService.matchMarketplaceProducts(null, List.of(ys), List.of(mg), 0.76d);

        assertEquals(1, response.size());
        verify(crossPlatformProductMatcherService).buildMarketplacePairs(List.of(ys), List.of(mg), 0.76d);
    }

    @Test
    void matchMarketplaceProductsPrefersManualPairWhenProvided() {
        MarketplaceProductCandidateResponse ysManual = new MarketplaceProductCandidateResponse(
                "YS", "ys-manual", "Sut A", "Brand", "img", null, null, null, null, null, null, null, null, null, null
        );
        MarketplaceProductCandidateResponse ysAuto = new MarketplaceProductCandidateResponse(
                "YS", "ys-auto", "Sut B", "Brand", "img", null, null, null, null, null, null, null, null, null, null
        );
        MarketplaceProductCandidateResponse mgManual = new MarketplaceProductCandidateResponse(
                "MG", "mg-manual", "Sut A", "Brand", "img", null, null, null, null, null, null, null, null, null, null
        );
        MarketplaceProductCandidateResponse mgAuto = new MarketplaceProductCandidateResponse(
                "MG", "mg-auto", "Sut B", "Brand", "img", null, null, null, null, null, null, null, null, null, null
        );
        MarketplaceProductMatchPairResponse autoPair = new MarketplaceProductMatchPairResponse(
                ysManual,
                mgAuto,
                new MarketplaceProductMatchScoreResponse(0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9),
                true,
                false
        );
        when(crossPlatformProductMatcherService.buildMarketplacePairs(
                List.of(ysManual, ysAuto),
                List.of(mgManual, mgAuto),
                0.76d
        )).thenReturn(List.of(autoPair));

        MarketplaceManualMatch manualMatch = new MarketplaceManualMatch();
        Category category = new Category();
        category.setId(9L);
        manualMatch.setCategory(category);
        manualMatch.setYsExternalId("ys-manual");
        manualMatch.setMgExternalId("mg-manual");
        when(marketplaceManualMatchRepository.findByCategoryId(9L)).thenReturn(List.of(manualMatch));

        List<MarketplaceProductMatchPairResponse> response = categoryService.matchMarketplaceProducts(
                9L,
                List.of(ysManual, ysAuto),
                List.of(mgManual, mgAuto),
                0.76d
        );

        assertEquals(1, response.size());
        assertEquals("ys-manual", response.getFirst().ys().externalId());
        assertEquals("mg-manual", response.getFirst().mg().externalId());
        assertTrue(response.getFirst().score().score() >= 1d);
    }

    @Test
    void saveManualMarketplaceMatchStoresNormalizedIds() {
        Category category = new Category();
        category.setId(3L);
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(marketplaceManualMatchRepository.findByCategoryIdAndYsExternalIdAndMgExternalId(3L, "ys-1", "mg-1"))
                .thenReturn(Optional.empty());

        categoryService.saveManualMarketplaceMatch(3L, " YS-1 ", " MG-1 ");

        verify(marketplaceManualMatchRepository).deleteByCategoryIdAndYsExternalId(3L, "ys-1");
        verify(marketplaceManualMatchRepository).deleteByCategoryIdAndMgExternalId(3L, "mg-1");
        verify(marketplaceManualMatchRepository).saveAndFlush(argThat(match ->
                match.getCategory() == category &&
                        "ys-1".equals(match.getYsExternalId()) &&
                        "mg-1".equals(match.getMgExternalId())
        ));
    }

    @Test
    void saveManualMarketplaceMatchReturnsWhenConflictAlreadyExistsAfterRetryCheck() {
        Category category = new Category();
        category.setId(4L);
        MarketplaceManualMatch existing = new MarketplaceManualMatch();
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(category));
        when(marketplaceManualMatchRepository.findByCategoryIdAndYsExternalIdAndMgExternalId(4L, "ys-2", "mg-2"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        doThrow(new DataIntegrityViolationException("dup"))
                .when(marketplaceManualMatchRepository)
                .saveAndFlush(any(MarketplaceManualMatch.class));

        assertDoesNotThrow(() -> categoryService.saveManualMarketplaceMatch(4L, "ys-2", "mg-2"));
    }

    @Test
    void listMarketplaceCandidatesInfersYsBrandFromMigrosCandidates() {
        Category category = new Category();
        category.setId(5L);
        category.setName("Kola");
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
        when(marketplaceCategorySearchService.fetchAll("Kola")).thenReturn(List.of(
                new MarketplaceCategoryFetchService.MarketplaceProductCandidate(
                        Marketplace.MG, "mg-1", "Coca Cola 1 L", "Coca-Cola", "mg.jpg",
                        new BigDecimal("40.00"), null, null, null, null, null, null, "ml", 1000, null
                ),
                new MarketplaceCategoryFetchService.MarketplaceProductCandidate(
                        Marketplace.YS, "ys-1", "Coca Cola 1 L", "", "ys.jpg",
                        new BigDecimal("39.00"), null, null, null, null, null, null, "ml", 1000, null
                )
        ));

        List<MarketplaceProductCandidateResponse> result = categoryService.listMarketplaceCandidates(5L);

        assertEquals(2, result.size());
        assertEquals("Coca-Cola", result.get(1).brandName());
    }

    @Test
    void listMarketplaceAddedProductsUsesCandidateFallbackAndPersistsMissingMetadata() {
        Category category = new Category();
        category.setId(8L);
        category.setName("Sut");
        when(categoryRepository.findById(8L)).thenReturn(Optional.of(category));
        MarketplaceProduct marketplaceProduct = new MarketplaceProduct();
        marketplaceProduct.setId(11L);
        marketplaceProduct.setMarketplace(Marketplace.MG);
        marketplaceProduct.setCategory(category);
        marketplaceProduct.setExternalId("mg-11");
        when(marketplaceProductRepository.findByMarketplaceAndCategory(Marketplace.MG, category))
                .thenReturn(List.of(marketplaceProduct));
        when(priceHistoryRepository.findByMarketplaceProductIds(List.of(11L))).thenReturn(List.of());
        when(marketplaceCategorySearchService.fetchAll("Sut")).thenReturn(List.of(
                new MarketplaceCategoryFetchService.MarketplaceProductCandidate(
                        Marketplace.MG,
                        "mg-11",
                        "Sek Sut 1 L",
                        "Sek",
                        "https://cdn.example.com/mg-11.jpg",
                        new BigDecimal("44.90"),
                        new BigDecimal("39.90"),
                        new BigDecimal("60"),
                        new BigDecimal("35.90"),
                        2,
                        1,
                        new BigDecimal("22.45"),
                        "ml",
                        1000,
                        null
                )
        ));

        List<MarketplaceProductEntryResponse> result = categoryService.listMarketplaceAddedProducts(8L, "MG");

        assertEquals(1, result.size());
        assertEquals("Sek Sut 1 L", result.getFirst().name());
        assertEquals("Sek", result.getFirst().brandName());
        assertEquals("https://cdn.example.com/mg-11.jpg", result.getFirst().imageUrl());
        verify(marketplaceProductRepository).saveAll(List.of(marketplaceProduct));
    }

    @Test
    void listAllMarketplaceAddedProductsReturnsGroupedResponses() {
        Category category = new Category();
        category.setId(9L);
        category.setName("Ayran");
        MarketplaceProduct marketplaceProduct = new MarketplaceProduct();
        marketplaceProduct.setId(21L);
        marketplaceProduct.setMarketplace(Marketplace.MG);
        marketplaceProduct.setCategory(category);
        marketplaceProduct.setExternalId("mg-21");
        marketplaceProduct.setBrandName("Pinar");
        marketplaceProduct.setImageUrl("img");
        PriceHistory history = new PriceHistory();
        Product product = new Product();
        product.setId(31L);
        product.setName("Pinar Ayran 1 L");
        history.setProduct(product);
        history.setMarketplaceProduct(marketplaceProduct);
        history.setPrice(new BigDecimal("50.00"));
        when(marketplaceProductRepository.findByMarketplace(Marketplace.MG)).thenReturn(List.of(marketplaceProduct));
        when(priceHistoryRepository.findByMarketplaceProductIds(List.of(21L))).thenReturn(List.of(history));

        var result = categoryService.listAllMarketplaceAddedProducts("MG");

        assertEquals(1, result.size());
        assertEquals(9L, result.getFirst().categoryId());
        assertEquals("Pinar Ayran 1 L", result.getFirst().name());
    }

    @Test
    void toProductSignatureRemovesAttachedQuantityAndPackTokens() throws Exception {
        assertEquals("coca cola", invokeProductSignature("Coca Cola 250g 2li Paket"));
        assertEquals("sek sut", invokeProductSignature("Sek Sut 1lt"));
    }

    private String invokeProductSignature(String value) throws Exception {
        Method method = CategoryService.class.getDeclaredMethod("toProductSignature", String.class);
        method.setAccessible(true);
        return (String) method.invoke(categoryService, value);
    }

}
