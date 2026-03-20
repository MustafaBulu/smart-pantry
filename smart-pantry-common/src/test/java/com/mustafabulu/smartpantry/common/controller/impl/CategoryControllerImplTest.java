package com.mustafabulu.smartpantry.common.controller.impl;

import com.mustafabulu.smartpantry.common.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.common.dto.request.CategoryRequest;
import com.mustafabulu.smartpantry.common.dto.request.MarketplaceManualMatchRequest;
import com.mustafabulu.smartpantry.common.dto.request.MarketplaceProductMatchRequest;
import com.mustafabulu.smartpantry.common.dto.response.CategoryResponse;
import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductCandidateResponse;
import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductMatchPairResponse;
import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductMatchScoreResponse;
import com.mustafabulu.smartpantry.common.service.CategoryService;
import com.mustafabulu.smartpantry.common.service.MarketplaceRequestResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryControllerImplTest {

    @Test
    void createCategoryReturnsResponse() {
        CategoryService service = mock(CategoryService.class);
        MarketplaceRequestResolver resolver = mock(MarketplaceRequestResolver.class);
        CategoryControllerImpl controller = new CategoryControllerImpl(service, resolver);
        when(service.createCategory("Snacks", null)).thenReturn(new CategoryResponse(1L, "Snacks"));

        var response = controller.createCategory(new CategoryRequest("Snacks"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Snacks", response.getBody().name());
    }

    @Test
    void createCategoryPassesMainCategory() {
        CategoryService service = mock(CategoryService.class);
        MarketplaceRequestResolver resolver = mock(MarketplaceRequestResolver.class);
        CategoryControllerImpl controller = new CategoryControllerImpl(service, resolver);
        when(service.createCategory("Snacks", "Temel Gida"))
                .thenReturn(new CategoryResponse(1L, "Snacks", "Temel Gida"));

        var response = controller.createCategory(new CategoryRequest("Snacks", "Temel Gida"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Temel Gida", response.getBody().mainCategory());
        verify(service).createCategory("Snacks", "Temel Gida");
    }

    @Test
    void listCategoriesReturnsList() {
        CategoryService service = mock(CategoryService.class);
        MarketplaceRequestResolver resolver = mock(MarketplaceRequestResolver.class);
        CategoryControllerImpl controller = new CategoryControllerImpl(service, resolver);
        when(service.listCategories()).thenReturn(List.of(new CategoryResponse(1L, "Snacks")));

        var response = controller.listCategories();

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void updateCategoryReturnsResponse() {
        CategoryService service = mock(CategoryService.class);
        MarketplaceRequestResolver resolver = mock(MarketplaceRequestResolver.class);
        CategoryControllerImpl controller = new CategoryControllerImpl(service, resolver);
        when(service.updateCategory(1L, "Snacks", null)).thenReturn(new CategoryResponse(1L, "Snacks"));

        var response = controller.updateCategory(1L, new CategoryRequest("Snacks"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Snacks", response.getBody().name());
    }

    @Test
    void updateCategoryPassesMainCategory() {
        CategoryService service = mock(CategoryService.class);
        MarketplaceRequestResolver resolver = mock(MarketplaceRequestResolver.class);
        CategoryControllerImpl controller = new CategoryControllerImpl(service, resolver);
        when(service.updateCategory(1L, "Snacks", "Temel Gida"))
                .thenReturn(new CategoryResponse(1L, "Snacks", "Temel Gida"));

        var response = controller.updateCategory(1L, new CategoryRequest("Snacks", "Temel Gida"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Temel Gida", response.getBody().mainCategory());
        verify(service).updateCategory(1L, "Snacks", "Temel Gida");
    }

    @Test
    void deleteCategoryReturnsMessage() {
        CategoryService service = mock(CategoryService.class);
        MarketplaceRequestResolver resolver = mock(MarketplaceRequestResolver.class);
        CategoryControllerImpl controller = new CategoryControllerImpl(service, resolver);

        var response = controller.deleteCategory(1L);

        assertEquals(ResponseMessages.CATEGORY_REMOVED, response.getBody());
    }

    @Test
    void matchMarketplaceProductsReturnsPairs() {
        CategoryService service = mock(CategoryService.class);
        MarketplaceRequestResolver resolver = mock(MarketplaceRequestResolver.class);
        CategoryControllerImpl controller = new CategoryControllerImpl(service, resolver);
        MarketplaceProductCandidateResponse ys = new MarketplaceProductCandidateResponse(
                "YS", "10", "Sut", "Brand", "img", null, null, null, null, null, null, null, null, null, null
        );
        MarketplaceProductCandidateResponse mg = new MarketplaceProductCandidateResponse(
                "MG", "20", "Sut", "Brand", "img", null, null, null, null, null, null, null, null, null, null
        );
        MarketplaceProductMatchPairResponse pair = new MarketplaceProductMatchPairResponse(
                ys,
                mg,
                new MarketplaceProductMatchScoreResponse(0.9, 0.9, 0.9, 0.9, 1, 1, 0.8, 0.7, 0.8),
                true,
                false
        );
        when(service.matchMarketplaceProducts(1L, List.of(ys), List.of(mg), 0.76d)).thenReturn(List.of(pair));

        var response = controller.matchMarketplaceProducts(new MarketplaceProductMatchRequest(
                1L,
                List.of(ys),
                List.of(mg),
                0.76d
        ));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("10", response.getBody().getFirst().ys().externalId());
        verify(service).matchMarketplaceProducts(1L, List.of(ys), List.of(mg), 0.76d);
    }

    @Test
    void saveManualMarketplaceMatchDelegatesToService() {
        CategoryService service = mock(CategoryService.class);
        MarketplaceRequestResolver resolver = mock(MarketplaceRequestResolver.class);
        CategoryControllerImpl controller = new CategoryControllerImpl(service, resolver);

        var response = controller.saveManualMarketplaceMatch(
                11L,
                new MarketplaceManualMatchRequest("ys-1", "mg-1")
        );

        assertEquals(200, response.getStatusCode().value());
        verify(service).saveManualMarketplaceMatch(11L, "ys-1", "mg-1");
    }

    @Test
    void deleteManualMarketplaceMatchDelegatesToService() {
        CategoryService service = mock(CategoryService.class);
        MarketplaceRequestResolver resolver = mock(MarketplaceRequestResolver.class);
        CategoryControllerImpl controller = new CategoryControllerImpl(service, resolver);

        var response = controller.deleteManualMarketplaceMatch(11L, "ys-1", "mg-1");

        assertEquals(200, response.getStatusCode().value());
        verify(service).deleteManualMarketplaceMatch(11L, "ys-1", "mg-1");
    }

}
