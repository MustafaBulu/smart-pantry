package com.mustafabulu.smartpantry.common.dto.request;

import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductCandidateResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Cross-platform marketplace product match request")
public record MarketplaceProductMatchRequest(
        @Schema(description = "Category id for loading manual matches", example = "12")
        Long categoryId,
        @Schema(description = "Yemeksepeti-side candidates")
        List<MarketplaceProductCandidateResponse> ys,
        @Schema(description = "Migros-side candidates")
        List<MarketplaceProductCandidateResponse> mg,
        @Schema(description = "Minimum match score threshold", example = "0.76")
        Double minScore
) {
}
