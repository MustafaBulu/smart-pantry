package com.mustafabulu.smartpantry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Marketplace product add request")
public record MarketplaceProductRequest(
        @Schema(description = "External product id from marketplace", example = "12345")
        @NotBlank
        String productId
) {
}
