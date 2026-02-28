package com.mustafabulu.smartpantry.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Reverse geocode for Yemeksepeti with coordinates")
public record YemeksepetiReverseGeocodeRequest(
        @Schema(description = "Latitude", example = "41.05335920163553")
        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,
        @Schema(description = "Longitude", example = "28.972704237666008")
        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude
) {
}
