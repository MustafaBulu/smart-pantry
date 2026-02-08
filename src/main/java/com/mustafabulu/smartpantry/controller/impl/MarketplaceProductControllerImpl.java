package com.mustafabulu.smartpantry.controller.impl;

import com.mustafabulu.smartpantry.controller.MarketplaceProductController;
import com.mustafabulu.smartpantry.dto.request.BulkAddRequest;
import com.mustafabulu.smartpantry.dto.response.BulkAddResponse;
import com.mustafabulu.smartpantry.dto.request.MarketplaceProductRequest;
import com.mustafabulu.smartpantry.dto.response.ProductResponse;
import com.mustafabulu.smartpantry.dto.request.ProductSearchRequest;
import com.mustafabulu.smartpantry.service.MarketplaceProductService;
import com.mustafabulu.smartpantry.service.ProductSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@Validated
@RequestMapping("/marketplaces")
public class MarketplaceProductControllerImpl implements MarketplaceProductController {

    private final MarketplaceProductService marketplaceProductService;
    private final ProductSearchService productSearchService;

    @PostMapping("/{marketplaceCode}/categories/{categoryName}/addproduct")
    @Override
    public ResponseEntity<String> addProduct(
            @PathVariable @NotBlank String marketplaceCode,
            @PathVariable @NotBlank String categoryName,
            @Valid @RequestBody MarketplaceProductRequest request
    ) {
        MarketplaceProductService.AddProductResult result = marketplaceProductService.addProduct(
                marketplaceCode,
                categoryName,
                request.productId()
        );
        return ResponseEntity.status(result.status()).body(result.message());
    }

    @PostMapping("/{marketplaceCode}/categories/{categoryName}/products:bulk")
    @Override
    public ResponseEntity<BulkAddResponse> addProductsBulk(
            @PathVariable @NotBlank String marketplaceCode,
            @PathVariable @NotBlank String categoryName,
            @Valid @RequestBody BulkAddRequest request
    ) {
        return ResponseEntity.ok(
                marketplaceProductService.addProducts(marketplaceCode, categoryName, request.productIds())
        );
    }

    @DeleteMapping("/{marketplaceCode}/products/{externalId}")
    @Override
    public ResponseEntity<String> deleteMarketplaceProduct(
            @PathVariable @NotBlank String marketplaceCode,
            @PathVariable @NotBlank String externalId,
            @RequestParam(required = false) String categoryName
    ) {
        MarketplaceProductService.DeleteMarketplaceProductResult result = marketplaceProductService
                .deleteMarketplaceProduct(marketplaceCode, externalId, categoryName);
        return ResponseEntity.status(result.status()).body(result.message());
    }

    @GetMapping("/products")
    @Override
    public ResponseEntity<List<ProductResponse>> searchProducts(@Valid ProductSearchRequest request) {
        return ResponseEntity.ok(productSearchService.search(request));
    }
}

