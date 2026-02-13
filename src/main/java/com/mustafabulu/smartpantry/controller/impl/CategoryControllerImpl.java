package com.mustafabulu.smartpantry.controller.impl;

import com.mustafabulu.smartpantry.controller.CategoryController;
import com.mustafabulu.smartpantry.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.dto.request.CategoryRequest;
import com.mustafabulu.smartpantry.dto.response.CategoryResponse;
import com.mustafabulu.smartpantry.dto.response.MarketplaceProductCandidateResponse;
import com.mustafabulu.smartpantry.dto.response.MarketplaceProductEntryResponse;
import com.mustafabulu.smartpantry.service.CategoryService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/categories")
public class CategoryControllerImpl implements CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @Override
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.createCategory(request.name()));
    }

    @GetMapping
    @Override
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        return ResponseEntity.ok(categoryService.listCategories());
    }

    @PutMapping("/{id}")
    @Override
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.ok(categoryService.updateCategory(id, request.name()));
    }

    @DeleteMapping("/{id}")
    @Override
    public ResponseEntity<String> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ResponseMessages.CATEGORY_REMOVED);
    }

    @GetMapping("/{id}/marketplace-products")
    @Override
    public ResponseEntity<List<MarketplaceProductCandidateResponse>> listMarketplaceCandidates(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.listMarketplaceCandidates(id));
    }

    @GetMapping("/{id}/marketplace-products/added")
    @Override
    public ResponseEntity<List<MarketplaceProductEntryResponse>> listMarketplaceProducts(
            @PathVariable Long id,
            @RequestParam(required = false) String marketplaceCode
    ) {
        return ResponseEntity.ok(categoryService.listMarketplaceAddedProducts(id, marketplaceCode));
    }
}

