package com.mustafabulu.smartpantry.controller;

import com.mustafabulu.smartpantry.dto.MarketplaceProductRequest;
import com.mustafabulu.smartpantry.dto.ProductResponse;
import com.mustafabulu.smartpantry.dto.ProductSearchRequest;
import com.mustafabulu.smartpantry.service.MarketplaceProductService;
import com.mustafabulu.smartpantry.service.ProductSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@AllArgsConstructor
@Validated
@RequestMapping("/marketplaces")
public class MarketplaceProductController {

    private final MarketplaceProductService marketplaceProductService;
    private final ProductSearchService productSearchService;

    @PostMapping("/{marketplaceCode}/categories/{categoryName}/addproduct")
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

    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> searchProducts(@Valid ProductSearchRequest request) {
        return ResponseEntity.ok(productSearchService.search(request));
    }
}
