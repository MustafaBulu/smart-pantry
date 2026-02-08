package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.dto.response.CategoryResponse;
import com.mustafabulu.smartpantry.core.exception.SPException;
import com.mustafabulu.smartpantry.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.repository.ProductRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MarketplaceProductRepository marketplaceProductRepository;

    public CategoryResponse createCategory(String name) {
        if (name == null || name.isBlank()) {
            throw new SPException(
                    HttpStatus.BAD_REQUEST,
                    ResponseMessages.CATEGORY_NAME_REQUIRED,
                    ResponseMessages.CATEGORY_NAME_REQUIRED_CODE
            );
        }
        String trimmedName = name.trim();
        return categoryRepository.findByName(trimmedName)
                .map(existing -> new CategoryResponse(existing.getId(), existing.getName()))
                .orElseGet(() -> {
                    Category created = new Category();
                    created.setName(trimmedName);
                    Category saved = categoryRepository.save(created);
                    return new CategoryResponse(saved.getId(), saved.getName());
                });
    }

    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .map(category -> new CategoryResponse(category.getId(), category.getName()))
                .toList();
    }

    public CategoryResponse updateCategory(Long id, String name) {
        if (id == null) {
            throw new SPException(
                    HttpStatus.BAD_REQUEST,
                    ResponseMessages.CATEGORY_NOT_FOUND,
                    ResponseMessages.CATEGORY_NOT_FOUND_CODE
            );
        }
        if (name == null || name.isBlank()) {
            throw new SPException(
                    HttpStatus.BAD_REQUEST,
                    ResponseMessages.CATEGORY_NAME_REQUIRED,
                    ResponseMessages.CATEGORY_NAME_REQUIRED_CODE
            );
        }
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new SPException(
                        HttpStatus.NOT_FOUND,
                        ResponseMessages.CATEGORY_NOT_FOUND,
                        ResponseMessages.CATEGORY_NOT_FOUND_CODE
                ));
        String trimmedName = name.trim();
        if (!trimmedName.equals(category.getName())
                && categoryRepository.existsByName(trimmedName)) {
            throw new SPException(
                    HttpStatus.CONFLICT,
                    ResponseMessages.CATEGORY_ALREADY_EXISTS,
                    ResponseMessages.CATEGORY_ALREADY_EXISTS_CODE
            );
        }
        category.setName(trimmedName);
        Category saved = categoryRepository.save(category);
        return new CategoryResponse(saved.getId(), saved.getName());
    }

    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new SPException(
                        HttpStatus.NOT_FOUND,
                        ResponseMessages.CATEGORY_NOT_FOUND,
                        ResponseMessages.CATEGORY_NOT_FOUND_CODE
                ));
        if (productRepository.existsByCategory(category)
                || marketplaceProductRepository.existsByCategory(category)) {
            throw new SPException(
                    HttpStatus.CONFLICT,
                    ResponseMessages.CATEGORY_IN_USE,
                    ResponseMessages.CATEGORY_IN_USE_CODE
            );
        }
        categoryRepository.delete(category);
    }

}

