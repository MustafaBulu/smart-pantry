package com.mustafabulu.smartpantry.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Catalog import request for category-id range based pagination")
public record CatalogCategoryRangeImportRequest(
        @Schema(description = "Marketplace code", example = "MG")
        String marketplaceCode,
        @Schema(description = "Source URL template", example = "https://www.migros.com.tr/rest/products/search?category-id=12&sayfa=1&sirala=onerilenler&reid=1772494121551000004")
        String sourceUrl,
        @Schema(description = "Start category-id (inclusive)", example = "2")
        Integer startCategoryId,
        @Schema(description = "End category-id (inclusive)", example = "10")
        Integer endCategoryId
) {
}
