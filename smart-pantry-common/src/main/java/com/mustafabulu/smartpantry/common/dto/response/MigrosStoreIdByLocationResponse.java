package com.mustafabulu.smartpantry.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resolved Migros store id information")
public record MigrosStoreIdByLocationResponse(
        @Schema(description = "Resolved store id", example = "20000000000281")
        Long storeId
) {
}
