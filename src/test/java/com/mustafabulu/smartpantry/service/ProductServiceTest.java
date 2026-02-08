package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.core.exception.SPException;

import com.mustafabulu.smartpantry.dto.request.ProductUpdateRequest;
import com.mustafabulu.smartpantry.dto.response.ProductDetailResponse;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.model.Product;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.repository.PriceHistoryRepository;
import com.mustafabulu.smartpantry.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void getProductThrowsWhenMissing() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(SPException.class, () -> productService.getProduct(1L));
    }

    @Test
    void updateProductAppliesChanges() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Snacks");
        Product product = new Product();
        product.setId(5L);
        product.setCategory(category);
        product.setName("Old");
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(categoryRepository.findByName("Snacks")).thenReturn(Optional.of(category));

        ProductUpdateRequest request = new ProductUpdateRequest("New", "Brand", "g", 100, "Snacks");
        ProductDetailResponse response = productService.updateProduct(5L, request);

        assertEquals("New", response.name());
        assertEquals("Brand", response.brand());
        assertEquals("g", response.unit());
        assertEquals(100, response.unitValue());
    }

    @ParameterizedTest
    @MethodSource("invalidUpdateRequests")
    void updateProductThrowsForInvalidRequests(ProductUpdateRequest request, String missingCategory) {
        Category category = new Category();
        category.setId(1L);
        category.setName("Snacks");
        Product product = new Product();
        product.setId(5L);
        product.setCategory(category);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        if (missingCategory != null) {
            when(categoryRepository.findByName(missingCategory)).thenReturn(Optional.empty());
        }

        assertThrows(SPException.class, () -> productService.updateProduct(5L, request));
    }

    @Test
    void updateProductThrowsWhenNoChanges() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Snacks");
        Product product = new Product();
        product.setId(5L);
        product.setCategory(category);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        ProductUpdateRequest request = new ProductUpdateRequest(null, null, null, null, null);
        assertThrows(SPException.class, () -> productService.updateProduct(5L, request));
    }

    @Test
    void updateProductThrowsWhenMissing() {
        when(productRepository.findById(5L)).thenReturn(Optional.empty());

        ProductUpdateRequest request = new ProductUpdateRequest("Chips", null, null, null, null);
        assertThrows(SPException.class, () -> productService.updateProduct(5L, request));
    }

    @Test
    void deleteProductRemovesHistoryAndProduct() {
        Product product = new Product();
        product.setId(4L);
        when(productRepository.findById(4L)).thenReturn(Optional.of(product));

        productService.deleteProduct(4L);

        verify(priceHistoryRepository).deleteByProductId(4L);
        verify(productRepository).delete(product);
    }

    @Test
    void deleteProductThrowsWhenMissing() {
        when(productRepository.findById(4L)).thenReturn(Optional.empty());

        assertThrows(SPException.class, () -> productService.deleteProduct(4L));
    }

    private static Stream<Arguments> invalidUpdateRequests() {
        return Stream.of(
                Arguments.of(new ProductUpdateRequest(" ", null, null, null, null), null),
                Arguments.of(new ProductUpdateRequest(null, null, null, null, " "), null),
                Arguments.of(new ProductUpdateRequest(null, null, null, null, "Other"), "Other")
        );
    }

    @Test
    void getProductMapsResponse() {
        Category category = new Category();
        category.setId(2L);
        category.setName("Snacks");
        Product product = new Product();
        product.setId(11L);
        product.setName("Chips");
        product.setCategory(category);
        when(productRepository.findById(11L)).thenReturn(Optional.of(product));

        ProductDetailResponse response = productService.getProduct(11L);

        assertEquals("Chips", response.name());
        assertEquals("Snacks", response.categoryName());
        assertNotNull(response.id());
    }
}
