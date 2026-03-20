package com.mustafabulu.smartpantry.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Category response")
public record CategoryResponse(
        @Schema(description = "Category id", example = "1")
        Long id,
        @Schema(description = "Category name", example = "Snacks")
        String name,
        @Schema(description = "Optional main category/group name", example = "Temel Gida")
        String mainCategory
) {
    public CategoryResponse(Long id, String name) {
        this(id, name, null);
    }
}
