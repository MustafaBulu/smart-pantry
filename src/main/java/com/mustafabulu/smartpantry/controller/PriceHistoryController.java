package com.mustafabulu.smartpantry.controller;

import com.mustafabulu.smartpantry.core.exception.ErrorData;
import com.mustafabulu.smartpantry.dto.response.CategoryPriceSummaryResponse;
import com.mustafabulu.smartpantry.dto.response.PriceHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "Price History", description = "Price history and summaries")
public interface PriceHistoryController {

    @Operation(summary = "Get price history for a product", description = "Defaults to the last 1 year.")
    @ApiResponse(responseCode = "200", description = "Price history returned")
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    @ApiResponse(responseCode = "404", description = "Product not found",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    ResponseEntity<List<PriceHistoryResponse>> getProductPrices(
            Long id,
            @Parameter(description = "Marketplace code filter", example = "YS")
            String marketplaceCode
    );

    @Operation(summary = "Get category price summary", description = "Defaults to the last 1 year.")
    @ApiResponse(responseCode = "200", description = "Category summary returned")
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    @ApiResponse(responseCode = "404", description = "Category not found",
            content = @Content(schema = @Schema(implementation = ErrorData.class)))
    ResponseEntity<List<CategoryPriceSummaryResponse>> getCategoryPrices(
            String name,
            @Parameter(description = "Marketplace code filter", example = "MG")
            String marketplaceCode
    );
}


