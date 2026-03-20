package com.mustafabulu.smartpantry.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Manual cross-marketplace match request")
public record MarketplaceManualMatchRequest(
        @Schema(description = "Yemeksepeti external product id", example = "194277819")
        String ysExternalId,
        @Schema(description = "Migros external product id", example = "46044909")
        String mgExternalId
) {
}

