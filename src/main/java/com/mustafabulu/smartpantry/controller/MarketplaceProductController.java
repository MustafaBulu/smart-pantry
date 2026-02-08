package com.mustafabulu.smartpantry.controller;

import com.mustafabulu.smartpantry.core.exception.ErrorData;
import com.mustafabulu.smartpantry.dto.request.BulkAddRequest;
import com.mustafabulu.smartpantry.dto.response.BulkAddResponse;
import com.mustafabulu.smartpantry.dto.request.MarketplaceProductRequest;
import com.mustafabulu.smartpantry.dto.response.ProductResponse;
import com.mustafabulu.smartpantry.dto.request.ProductSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "Marketplaces", description = "Marketplace product management APIs")
public interface MarketplaceProductController {

    @Operation(summary = "Add a product to a marketplace category")
    @ApiResponse(responseCode = "200", description = "Product already tracked",
            content = @Content(schema = @Schema(implementation = String.class)))
    @ApiResponse(responseCode = "201", description = "Product created",
            content = @Content(schema = @Schema(implementation = String.class)))
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    @ApiResponse(responseCode = "404", description = "Category or product not found",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    ResponseEntity<String> addProduct(
            String marketplaceCode,
            String categoryName,
            MarketplaceProductRequest request
    );

    @Operation(summary = "Bulk add marketplace products to a category")
    @ApiResponse(responseCode = "200", description = "Bulk add completed")
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    @ApiResponse(responseCode = "404", description = "Category not found",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    ResponseEntity<BulkAddResponse> addProductsBulk(
            String marketplaceCode,
            String categoryName,
            BulkAddRequest request
    );

    @Operation(summary = "Refresh a marketplace product and record price history")
    @ApiResponse(responseCode = "200", description = "Product refreshed",
            content = @Content(schema = @Schema(implementation = String.class)))
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    @ApiResponse(responseCode = "404", description = "Marketplace product not found",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    @ApiResponse(responseCode = "409", description = "Ambiguous marketplace product",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    ResponseEntity<String> deleteMarketplaceProduct(
            String marketplaceCode,
            String externalId,
            @Parameter(description = "Optional category name to disambiguate", example = "Snacks")
            String categoryName
    );

    @Operation(summary = "Search products by marketplace and/or category")
    @ApiResponse(responseCode = "200", description = "Product list returned")
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    ResponseEntity<List<ProductResponse>> searchProducts(ProductSearchRequest request);
}

