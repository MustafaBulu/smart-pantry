package com.mustafabulu.smartpantry.common.controller;

import com.mustafabulu.smartpantry.common.core.exception.ErrorData;
import com.mustafabulu.smartpantry.common.dto.response.ProductDetailResponse;
import com.mustafabulu.smartpantry.common.dto.request.ProductUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Products", description = "Product management APIs")
public interface ProductController {

    @Operation(summary = "Get product details")
    @ApiResponse(responseCode = "200", description = "Product returned")
    @ApiResponse(responseCode = "404", description = "Product not found",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    ResponseEntity<ProductDetailResponse> getProduct(Long id);

    @Operation(summary = "Update product fields")
    @ApiResponse(responseCode = "200", description = "Product updated")
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    @ApiResponse(responseCode = "404", description = "Product not found",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    ResponseEntity<ProductDetailResponse> updateProduct(Long id, ProductUpdateRequest request);

    @Operation(summary = "Delete product")
    @ApiResponse(responseCode = "200", description = "Product removed",
            content = @Content(schema = @Schema(implementation = String.class)))
    @ApiResponse(responseCode = "404", description = "Product not found",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    ResponseEntity<String> deleteProduct(Long id);
}

