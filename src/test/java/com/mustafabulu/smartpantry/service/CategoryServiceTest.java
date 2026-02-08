package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.core.exception.SPException;
import com.mustafabulu.smartpantry.dto.response.CategoryResponse;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MarketplaceProductRepository marketplaceProductRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createCategoryReturnsExistingWhenFound() {
        Category category = new Category();
        category.setId(10L);
        category.setName("Snacks");
        when(categoryRepository.findByName("Snacks")).thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.createCategory("Snacks");

        assertEquals(10L, response.id());
        assertEquals("Snacks", response.name());
        verify(categoryRepository, never()).save(category);
    }

    @Test
    void createCategoryCreatesWhenMissing() {
        when(categoryRepository.findByName("Snacks")).thenReturn(Optional.empty());
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
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByName("New")).thenReturn(true);

        assertThrows(SPException.class, () -> categoryService.updateCategory(1L, "New"));
    }

    @Test
    void updateCategoryUpdatesName() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Old");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByName("New")).thenReturn(false);
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryResponse response = categoryService.updateCategory(1L, "New");

        assertEquals("New", response.name());
    }

    @Test
    void deleteCategoryThrowsWhenInUse() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Snacks");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategory(category)).thenReturn(true);

        assertThrows(SPException.class, () -> categoryService.deleteCategory(1L));
        verify(categoryRepository, never()).delete(category);
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
        when(productRepository.existsByCategory(category)).thenReturn(false);
        when(marketplaceProductRepository.existsByCategory(category)).thenReturn(false);

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
