package com.mustafabulu.smartpantry.common.controller.impl;

import com.mustafabulu.smartpantry.common.dto.request.BulkAddRequest;
import com.mustafabulu.smartpantry.common.dto.request.MarketplaceProductRequest;
import com.mustafabulu.smartpantry.common.dto.request.ProductSearchRequest;
import com.mustafabulu.smartpantry.common.dto.response.BulkAddResponse;
import com.mustafabulu.smartpantry.common.dto.response.ProductResponse;
import com.mustafabulu.smartpantry.common.service.MarketplaceRequestResolver;
import com.mustafabulu.smartpantry.common.service.MarketplaceProductService;
import com.mustafabulu.smartpantry.common.service.ProductSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketplaceProductControllerImplTest {

    @Test
    void addProductReturnsServiceStatus() {
        MarketplaceProductService marketplaceService = mock(MarketplaceProductService.class);
        ProductSearchService searchService = mock(ProductSearchService.class);
        MarketplaceRequestResolver resolver = mock(MarketplaceRequestResolver.class);
        MarketplaceProductControllerImpl controller = new MarketplaceProductControllerImpl(
                marketplaceService,
                searchService,
                resolver
        );
        when(resolver.resolveRequired("YS")).thenReturn("YS");
        when(marketplaceService.addProduct("YS", "Snacks", "1"))
                .thenReturn(new MarketplaceProductService.AddProductResult(HttpStatus.CREATED, "ok"));

        var response = controller.addProduct("YS", "Snacks", new MarketplaceProductRequest("1"));

        assertEquals(201, response.getStatusCode().value());
    }

    @Test
    void bulkAddReturnsResponse() {
        MarketplaceProductService marketplaceService = mock(MarketplaceProductService.class);
        ProductSearchService searchService = mock(ProductSearchService.class);
        MarketplaceRequestResolver resolver = mock(MarketplaceRequestResolver.class);
        MarketplaceProductControllerImpl controller = new MarketplaceProductControllerImpl(
                marketplaceService,
                searchService,
                resolver
        );
        when(resolver.resolveRequired("YS")).thenReturn("YS");
        BulkAddResponse bulkResponse = new BulkAddResponse(1, 1, 0, 0, List.of());
        when(marketplaceService.addProducts("YS", "Snacks", List.of("1"))).thenReturn(bulkResponse);

        var response = controller.addProductsBulk("YS", "Snacks", new BulkAddRequest(List.of("1")));

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().created());
    }

    @Test
    void deleteMarketplaceProductReturnsStatus() {
        MarketplaceProductService marketplaceService = mock(MarketplaceProductService.class);
        ProductSearchService searchService = mock(ProductSearchService.class);
        MarketplaceRequestResolver resolver = mock(MarketplaceRequestResolver.class);
        MarketplaceProductControllerImpl controller = new MarketplaceProductControllerImpl(
                marketplaceService,
                searchService,
                resolver
        );
        when(resolver.resolveRequired("YS")).thenReturn("YS");
        when(marketplaceService.deleteMarketplaceProduct("YS", "1", null))
                .thenReturn(new MarketplaceProductService.DeleteMarketplaceProductResult(HttpStatus.OK, "ok", 1));

        var response = controller.deleteMarketplaceProduct("YS", "1", null);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void searchProductsReturnsList() {
        MarketplaceProductService marketplaceService = mock(MarketplaceProductService.class);
        ProductSearchService searchService = mock(ProductSearchService.class);
        MarketplaceRequestResolver resolver = mock(MarketplaceRequestResolver.class);
        MarketplaceProductControllerImpl controller = new MarketplaceProductControllerImpl(
                marketplaceService,
                searchService,
                resolver
        );
        when(resolver.resolveOptional("YS")).thenReturn("YS");
        when(searchService.search(new ProductSearchRequest("YS", "Snacks")))
                .thenReturn(List.of(new ProductResponse(1L, "Chips", BigDecimal.TEN)));

        var response = controller.searchProducts(new ProductSearchRequest("YS", "Snacks"));

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }
}
