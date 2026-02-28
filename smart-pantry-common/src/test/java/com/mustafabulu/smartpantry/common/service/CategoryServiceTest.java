package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.response.CategoryResponse;
import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.repository.CategoryRepository;
import com.mustafabulu.smartpantry.common.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.common.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
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

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createCategoryThrowsWhenAlreadyExists() {
        Category category = new Category();
        category.setId(10L);
        category.setName("Snacks");
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.of(category));

        assertThrows(SPException.class, () -> categoryService.createCategory("Snacks"));
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

        CategoryResponse response = categoryService.createCategory("Snacks");

        assertEquals(5L, response.id());
        assertEquals("Snacks", response.name());
    }

    @Test
    void createCategoryThrowsWhenBlank() {
        assertThrows(SPException.class, () -> categoryService.createCategory(" "));
    }

    @Test
    void createCategoryThrowsWhenNull() {
        assertThrows(SPException.class, () -> categoryService.createCategory(null));
    }

    @Test
    void updateCategoryThrowsWhenMissing() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(SPException.class, () -> categoryService.updateCategory(1L, "Snacks"));
    }

    @Test
    void updateCategoryThrowsWhenIdNull() {
        assertThrows(SPException.class, () -> categoryService.updateCategory(null, "Snacks"));
    }

    @Test
    void updateCategoryThrowsWhenNameBlank() {
        assertThrows(SPException.class, () -> categoryService.updateCategory(1L, " "));
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

        assertThrows(SPException.class, () -> categoryService.updateCategory(1L, "New"));
    }

    @Test
    void updateCategoryUpdatesName() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Old");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCase("New")).thenReturn(Optional.empty());
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryResponse response = categoryService.updateCategory(1L, "New");

        assertEquals("New", response.name());
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
        when(categoryRepository.findAll()).thenReturn(List.of(category));

        List<CategoryResponse> responses = categoryService.listCategories();

        assertEquals(1, responses.size());
        assertEquals("Snacks", responses.getFirst().name());
    }
}
