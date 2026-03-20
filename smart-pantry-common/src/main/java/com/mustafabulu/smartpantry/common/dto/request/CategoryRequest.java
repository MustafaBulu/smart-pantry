package com.mustafabulu.smartpantry.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Create or update category request")
public record CategoryRequest(
        @Schema(description = "Category name", example = "Snacks")
        @NotBlank
        String name,
        @Schema(description = "Optional main category/group name", example = "Temel Gida")
        String mainCategory
) {
    public CategoryRequest(String name) {
        this(name, null);
    }
}
