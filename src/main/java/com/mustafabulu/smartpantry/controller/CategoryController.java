package com.mustafabulu.smartpantry.controller;

import com.mustafabulu.smartpantry.core.exception.ErrorData;
import com.mustafabulu.smartpantry.dto.request.CategoryRequest;
import com.mustafabulu.smartpantry.dto.response.CategoryResponse;
import com.mustafabulu.smartpantry.dto.response.MarketplaceProductCandidateResponse;
import com.mustafabulu.smartpantry.dto.response.MarketplaceProductEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "Categories", description = "Category management APIs")
public interface CategoryController {

    @Operation(summary = "Create category")
    @ApiResponse(responseCode = "200", description = "Category returned")
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    ResponseEntity<CategoryResponse> createCategory(CategoryRequest request);

    @Operation(summary = "List categories")
    @ApiResponse(responseCode = "200", description = "Category list returned")
    ResponseEntity<List<CategoryResponse>> listCategories();

    @Operation(summary = "Update category name")
    @ApiResponse(responseCode = "200", description = "Category updated")
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    @ApiResponse(responseCode = "404", description = "Category not found",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    @ApiResponse(responseCode = "409", description = "Category already exists",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    ResponseEntity<CategoryResponse> updateCategory(Long id, CategoryRequest request);

    @Operation(summary = "Delete category")
    @ApiResponse(responseCode = "200", description = "Category removed",
            content = @Content(schema = @Schema(implementation = String.class)))
    @ApiResponse(responseCode = "404", description = "Category not found",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    @ApiResponse(responseCode = "409", description = "Category in use",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    ResponseEntity<String> deleteCategory(Long id);

    @Operation(summary = "List marketplace candidates for a category")
    @ApiResponse(responseCode = "200", description = "Marketplace candidates returned")
    @ApiResponse(responseCode = "404", description = "Category not found",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    ResponseEntity<List<MarketplaceProductCandidateResponse>> listMarketplaceCandidates(Long id);

    @Operation(summary = "List added marketplace products for a category")
    @ApiResponse(responseCode = "200", description = "Marketplace products returned")
    @ApiResponse(responseCode = "404", description = "Category not found",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    ResponseEntity<List<MarketplaceProductEntryResponse>> listMarketplaceProducts(
            Long id,
            String marketplaceCode
    );
}
