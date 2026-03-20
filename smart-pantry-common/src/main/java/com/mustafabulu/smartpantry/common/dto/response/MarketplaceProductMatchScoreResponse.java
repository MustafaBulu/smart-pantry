package com.mustafabulu.smartpantry.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cross-platform match score details")
public record MarketplaceProductMatchScoreResponse(
        @Schema(description = "Total weighted score", example = "0.84")
        double score,
        @Schema(description = "Token-level name similarity score", example = "0.73")
        double nameScore,
        @Schema(description = "Core-token name similarity score", example = "0.70")
        double coreNameScore,
        @Schema(description = "Phrase similarity score", example = "0.81")
        double phraseScore,
        @Schema(description = "Quantity compatibility score", example = "1.0")
        double quantityScore,
        @Schema(description = "Brand similarity score", example = "1.0")
        double brandScore,
        @Schema(description = "Image similarity score", example = "0.90")
        double imageScore,
        @Schema(description = "Price consistency score", example = "0.85")
        double priceScore,
        @Schema(description = "Profile compatibility score", example = "0.80")
        double profileScore
) {
}
