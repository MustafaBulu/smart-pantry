package com.mustafabulu.smartpantry.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Resolve Migros store id from coordinates")
public record MigrosStoreIdByLocationRequest(
        @Schema(description = "Latitude", example = "41.052993")
        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,
        @Schema(description = "Longitude", example = "28.9733272")
        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude
) {
}
