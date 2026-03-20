package com.mustafabulu.smartpantry.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Matched cross-platform marketplace product pair")
public record MarketplaceProductMatchPairResponse(
        @Schema(description = "Yemeksepeti-side candidate")
        MarketplaceProductCandidateResponse ys,
        @Schema(description = "Migros-side candidate")
        MarketplaceProductCandidateResponse mg,
        @Schema(description = "Detailed match score")
        MarketplaceProductMatchScoreResponse score,
        @Schema(description = "Auto-link eligibility decision", example = "true")
        boolean autoLinkEligible,
        @Schema(description = "True if pair is a user-provided manual match", example = "false")
        boolean manualMatch
) {
}
