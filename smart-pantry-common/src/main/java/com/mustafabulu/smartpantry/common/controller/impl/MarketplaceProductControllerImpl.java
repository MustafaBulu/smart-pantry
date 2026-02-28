package com.mustafabulu.smartpantry.common.controller.impl;

import com.mustafabulu.smartpantry.common.controller.MarketplaceProductController;
import com.mustafabulu.smartpantry.common.dto.request.BulkAddRequest;
import com.mustafabulu.smartpantry.common.dto.response.BulkAddResponse;
import com.mustafabulu.smartpantry.common.dto.request.MarketplaceProductRequest;
import com.mustafabulu.smartpantry.common.dto.response.ProductResponse;
import com.mustafabulu.smartpantry.common.dto.request.ProductSearchRequest;
import com.mustafabulu.smartpantry.common.service.MarketplaceRequestResolver;
import com.mustafabulu.smartpantry.common.service.MarketplaceProductService;
import com.mustafabulu.smartpantry.common.service.ProductSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequestMapping("/marketplaces")
public class MarketplaceProductControllerImpl implements MarketplaceProductController {

    private final MarketplaceProductService marketplaceProductService;
    private final ProductSearchService productSearchService;
    private final MarketplaceRequestResolver marketplaceRequestResolver;

    @PostMapping("/{marketplaceCode}/categories/{categoryName}/addproduct")
    @Override
    public ResponseEntity<String> addProduct(
            @PathVariable @NotBlank String marketplaceCode,
            @PathVariable @NotBlank String categoryName,
            @Valid @RequestBody MarketplaceProductRequest request
    ) {
        log.info(
                "addProduct request received: marketplaceCode={}, categoryName={}, productId={}",
                marketplaceCode,
                categoryName,
                request.productId()
        );
        String resolvedMarketplaceCode = marketplaceRequestResolver.resolveRequired(marketplaceCode);
        MarketplaceProductService.AddProductResult result = marketplaceProductService.addProduct(
                resolvedMarketplaceCode,
                categoryName,
                request.productId()
        );
        log.info(
                "addProduct request completed: marketplaceCode={}, resolvedMarketplaceCode={}, categoryName={}, productId={}, status={}",
                marketplaceCode,
                resolvedMarketplaceCode,
                categoryName,
                request.productId(),
                result.status().value()
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
        List<String> productIds = request.productIds() == null ? List.of() : request.productIds();
        log.info(
                "bulk add request received: marketplaceCode={}, categoryName={}, productCount={}",
                marketplaceCode,
                categoryName,
                productIds.size()
        );
        String resolvedMarketplaceCode = marketplaceRequestResolver.resolveRequired(marketplaceCode);
        BulkAddResponse response = marketplaceProductService.addProducts(
                resolvedMarketplaceCode,
                categoryName,
                productIds
        );
        log.info(
                "bulk add request completed: marketplaceCode={}, resolvedMarketplaceCode={}, categoryName={}, requested={}, created={}, updated={}, failed={}",
                marketplaceCode,
                resolvedMarketplaceCode,
                categoryName,
                response.requested(),
                response.created(),
                response.updated(),
                response.failed()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/categories/{categoryName}/addproduct")
    public ResponseEntity<String> addProductDefaultMarketplace(
            @PathVariable @NotBlank String categoryName,
            @Valid @RequestBody MarketplaceProductRequest request
    ) {
        String marketplaceCode = marketplaceRequestResolver.resolveRequired(null);
        MarketplaceProductService.AddProductResult result = marketplaceProductService.addProduct(
                marketplaceCode,
                categoryName,
                request.productId()
        );
        return ResponseEntity.status(result.status()).body(result.message());
    }

    @PostMapping("/categories/{categoryName}/products:bulk")
    public ResponseEntity<BulkAddResponse> addProductsBulkDefaultMarketplace(
            @PathVariable @NotBlank String categoryName,
            @Valid @RequestBody BulkAddRequest request
    ) {
        String marketplaceCode = marketplaceRequestResolver.resolveRequired(null);
        List<String> productIds = request.productIds() == null ? List.of() : request.productIds();
        return ResponseEntity.ok(
                marketplaceProductService.addProducts(marketplaceCode, categoryName, productIds)
        );
    }

    @DeleteMapping("/{marketplaceCode}/products/{externalId}")
    @Override
    public ResponseEntity<String> deleteMarketplaceProduct(
            @PathVariable @NotBlank String marketplaceCode,
            @PathVariable @NotBlank String externalId,
            @RequestParam(required = false) String categoryName
    ) {
        log.info(
                "deleteMarketplaceProduct request received: marketplaceCode={}, externalId={}, categoryName={}",
                marketplaceCode,
                externalId,
                categoryName
        );
        String resolvedMarketplaceCode = marketplaceRequestResolver.resolveRequired(marketplaceCode);
        MarketplaceProductService.DeleteMarketplaceProductResult result = marketplaceProductService
                .deleteMarketplaceProduct(resolvedMarketplaceCode, externalId, categoryName);
        log.info(
                "deleteMarketplaceProduct request completed: marketplaceCode={}, resolvedMarketplaceCode={}, externalId={}, categoryName={}, status={}, deletedCount={}",
                marketplaceCode,
                resolvedMarketplaceCode,
                externalId,
                categoryName,
                result.status().value(),
                result.deletedCount()
        );
        return ResponseEntity.status(result.status()).body(result.message());
    }

    @DeleteMapping("/products/{externalId}")
    public ResponseEntity<String> deleteMarketplaceProductDefaultMarketplace(
            @PathVariable @NotBlank String externalId,
            @RequestParam(required = false) String categoryName
    ) {
        String marketplaceCode = marketplaceRequestResolver.resolveRequired(null);
        MarketplaceProductService.DeleteMarketplaceProductResult result = marketplaceProductService
                .deleteMarketplaceProduct(marketplaceCode, externalId, categoryName);
        return ResponseEntity.status(result.status()).body(result.message());
    }

    @GetMapping("/products")
    @Override
    public ResponseEntity<List<ProductResponse>> searchProducts(@Valid ProductSearchRequest request) {
        String resolvedMarketplaceCode = marketplaceRequestResolver.resolveOptional(request.marketplaceCode());
        ProductSearchRequest resolvedRequest = new ProductSearchRequest(
                resolvedMarketplaceCode,
                request.categoryName()
        );
        return ResponseEntity.ok(productSearchService.search(resolvedRequest));
    }
}
