package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.dto.CategoryResponse;
import com.mustafabulu.smartpantry.core.exception.SPException;
import com.mustafabulu.smartpantry.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

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


}
