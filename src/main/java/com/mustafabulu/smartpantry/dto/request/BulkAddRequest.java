package com.mustafabulu.smartpantry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Bulk add request")
public record BulkAddRequest(
        @Schema(description = "List of marketplace product ids", example = "[\"12345\",\"67890\"]")
        @NotEmpty
        List<@NotBlank String> productIds
) {
}
