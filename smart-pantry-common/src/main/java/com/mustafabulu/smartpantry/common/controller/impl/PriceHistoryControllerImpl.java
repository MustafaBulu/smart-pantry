package com.mustafabulu.smartpantry.common.controller.impl;

import com.mustafabulu.smartpantry.common.controller.PriceHistoryController;
import com.mustafabulu.smartpantry.common.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.common.dto.response.CategoryPriceSummaryResponse;
import com.mustafabulu.smartpantry.common.dto.response.PriceHistoryResponse;
import com.mustafabulu.smartpantry.common.service.MarketplaceRequestResolver;
import com.mustafabulu.smartpantry.common.service.PriceHistoryService;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@Validated
@RequestMapping
public class PriceHistoryControllerImpl implements PriceHistoryController {

    private final PriceHistoryService priceHistoryService;
    private final MarketplaceRequestResolver marketplaceRequestResolver;

    @GetMapping("/products/{id}/prices")
    @Override
    public ResponseEntity<List<PriceHistoryResponse>> getProductPrices(
            @PathVariable Long id,
            @RequestParam(required = false)
            @Pattern(regexp = "(?i)^(YS|MG)$", message = ResponseMessages.INVALID_MARKETPLACE_CODE)
            String marketplaceCode,
            @RequestParam(required = false, defaultValue = "false")
            Boolean useMoneyPrice,
            @RequestParam(required = false, defaultValue = "false")
            Boolean useEffectivePrice
    ) {
        return ResponseEntity.ok(
                priceHistoryService.getProductPrices(
                        id,
                        marketplaceRequestResolver.resolveOptional(marketplaceCode),
                        null,
                        null,
                        Boolean.TRUE.equals(useMoneyPrice),
                        Boolean.TRUE.equals(useEffectivePrice)
                )
        );
    }

    @GetMapping("/categories/{name}/prices")
    @Override
    public ResponseEntity<List<CategoryPriceSummaryResponse>> getCategoryPrices(
            @PathVariable String name,
            @RequestParam(required = false)
            @Pattern(regexp = "(?i)^(YS|MG)$", message = ResponseMessages.INVALID_MARKETPLACE_CODE)
            String marketplaceCode
    ) {
        return ResponseEntity.ok(
                priceHistoryService.getCategorySummary(
                        name,
                        marketplaceRequestResolver.resolveOptional(marketplaceCode),
                        null,
                        null
                )
        );
    }
}

