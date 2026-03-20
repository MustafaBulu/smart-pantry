package com.mustafabulu.smartpantry.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Catalog import request from direct marketplace URLs")
public record CatalogImportRequest(
        @Schema(description = "Marketplace code", example = "MG")
        String marketplaceCode,
        @Schema(description = "Source URLs to crawl", example = "[\"https://www.migros.com.tr/rest/products/search?discount-type=ALL_DISCOUNTS&sayfa=1&sirala=cok-satanlar&reid=1772492322967000001\"]")
        List<String> urls
) {
}
