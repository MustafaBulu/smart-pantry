package com.mustafabulu.smartpantry.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Platform-based minimum basket amount settings")
public record BasketMinimumSettingsResponse(
        @Schema(description = "Yemeksepeti minimum basket amount", example = "150.00")
        java.math.BigDecimal ysMinimumBasketAmount,
        @Schema(description = "Migros minimum basket amount", example = "250.00")
        java.math.BigDecimal mgMinimumBasketAmount
) {
}
