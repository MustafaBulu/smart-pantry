package com.mustafabulu.smartpantry.controller.impl;

import com.mustafabulu.smartpantry.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.dto.request.CategoryRequest;
import com.mustafabulu.smartpantry.dto.response.CategoryResponse;
import com.mustafabulu.smartpantry.service.CategoryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryControllerImplTest {

    @Test
    void createCategoryReturnsResponse() {
        CategoryService service = mock(CategoryService.class);
        CategoryControllerImpl controller = new CategoryControllerImpl(service);
        when(service.createCategory("Snacks")).thenReturn(new CategoryResponse(1L, "Snacks"));

        var response = controller.createCategory(new CategoryRequest("Snacks"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Snacks", response.getBody().name());
    }

    @Test
    void listCategoriesReturnsList() {
        CategoryService service = mock(CategoryService.class);
        CategoryControllerImpl controller = new CategoryControllerImpl(service);
        when(service.listCategories()).thenReturn(List.of(new CategoryResponse(1L, "Snacks")));

        var response = controller.listCategories();

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void updateCategoryReturnsResponse() {
        CategoryService service = mock(CategoryService.class);
        CategoryControllerImpl controller = new CategoryControllerImpl(service);
        when(service.updateCategory(1L, "Snacks")).thenReturn(new CategoryResponse(1L, "Snacks"));

        var response = controller.updateCategory(1L, new CategoryRequest("Snacks"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Snacks", response.getBody().name());
    }

    @Test
    void deleteCategoryReturnsMessage() {
        CategoryService service = mock(CategoryService.class);
        CategoryControllerImpl controller = new CategoryControllerImpl(service);

        var response = controller.deleteCategory(1L);

        assertEquals(ResponseMessages.CATEGORY_REMOVED, response.getBody());
    }
}
