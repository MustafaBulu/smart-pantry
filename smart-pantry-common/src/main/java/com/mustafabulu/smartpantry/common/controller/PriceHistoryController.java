package com.mustafabulu.smartpantry.common.controller;

import com.mustafabulu.smartpantry.common.core.exception.ErrorData;
import com.mustafabulu.smartpantry.common.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.common.dto.response.CategoryPriceSummaryResponse;
import com.mustafabulu.smartpantry.common.dto.response.PriceHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
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
            @Pattern(regexp = "(?i)^(YS|MG)$", message = ResponseMessages.INVALID_MARKETPLACE_CODE)
            String marketplaceCode,
            @Parameter(description = "Use Migros Money price if available", example = "true")
            Boolean useMoneyPrice,
            @Parameter(description = "Use effective campaign price if available", example = "true")
            Boolean useEffectivePrice
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
            @Pattern(regexp = "(?i)^(YS|MG)$", message = ResponseMessages.INVALID_MARKETPLACE_CODE)
            String marketplaceCode
    );
}


