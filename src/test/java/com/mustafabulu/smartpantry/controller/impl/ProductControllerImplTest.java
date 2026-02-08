package com.mustafabulu.smartpantry.controller.impl;

import com.mustafabulu.smartpantry.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.dto.request.ProductUpdateRequest;
import com.mustafabulu.smartpantry.dto.response.ProductDetailResponse;
import com.mustafabulu.smartpantry.service.ProductService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductControllerImplTest {

    @Test
    void getProductReturnsResponse() {
        ProductService service = mock(ProductService.class);
        ProductControllerImpl controller = new ProductControllerImpl(service);
        when(service.getProduct(1L)).thenReturn(new ProductDetailResponse(1L, "Chips", null, null, null, 1L, "Snacks", LocalDateTime.now()));

        var response = controller.getProduct(1L);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Chips", response.getBody().name());
    }

    @Test
    void updateProductReturnsResponse() {
        ProductService service = mock(ProductService.class);
        ProductControllerImpl controller = new ProductControllerImpl(service);
        ProductDetailResponse detail = new ProductDetailResponse(1L, "Chips", null, null, null, 1L, "Snacks", LocalDateTime.now());
        ProductUpdateRequest request = new ProductUpdateRequest("Chips", null, null, null, null);
        when(service.updateProduct(1L, request)).thenReturn(detail);

        var response = controller.updateProduct(1L, request);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void deleteProductReturnsMessage() {
        ProductService service = mock(ProductService.class);
        ProductControllerImpl controller = new ProductControllerImpl(service);

        var response = controller.deleteProduct(1L);

        assertNotNull(response.getBody());
        assertEquals(ResponseMessages.PRODUCT_REMOVED, response.getBody());
    }
}
