package com.mustafabulu.smartpantry.common.dto.request;

import com.mustafabulu.smartpantry.common.core.response.ResponseMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Product search request")
public record ProductSearchRequest(
        @Schema(description = "Marketplace code", example = "YS")
        @Pattern(regexp = "(?i)^(YS|MG)$", message = ResponseMessages.INVALID_MARKETPLACE_CODE)
        String marketplaceCode,
        @Schema(description = "Category name filter", example = "Snacks")
        String categoryName
) {
}
