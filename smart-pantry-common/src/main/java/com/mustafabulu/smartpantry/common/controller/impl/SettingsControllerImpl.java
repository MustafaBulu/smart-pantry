package com.mustafabulu.smartpantry.common.controller.impl;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.response.BasketMinimumSettingsResponse;
import com.mustafabulu.smartpantry.common.dto.response.DailyProductDetailsTriggerResponse;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.service.BasketSettingsService;
import com.mustafabulu.smartpantry.common.service.DailyProductDetailsTriggerService;
import com.mustafabulu.smartpantry.common.service.MarketplaceRequestResolver;
import com.mustafabulu.smartpantry.common.service.SettingsAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settings")
public class SettingsControllerImpl {

    private final BasketSettingsService basketSettingsService;
    private final DailyProductDetailsTriggerService dailyProductDetailsTriggerService;
    private final MarketplaceRequestResolver marketplaceRequestResolver;
    private final SettingsAccessGuard settingsAccessGuard;

    @GetMapping("/basket-minimums")
    public ResponseEntity<BasketMinimumSettingsResponse> getBasketMinimumSettings() {
        return ResponseEntity.ok(basketSettingsService.getMinimumBasketSettings());
    }

    @PostMapping("/daily-details/trigger")
    public ResponseEntity<DailyProductDetailsTriggerResponse> triggerDailyProductDetails(
            @RequestParam(required = false) String marketplaceCode,
            @RequestHeader(value = SettingsAccessGuard.SETTINGS_API_KEY_HEADER, required = false) String settingsApiKey
    ) {
        settingsAccessGuard.assertAuthorized(settingsApiKey);
        Marketplace marketplace = Marketplace.fromCode(
                marketplaceRequestResolver.resolveRequired(marketplaceCode)
        );
        if (marketplace == null) {
            throw new SPException(
                    BAD_REQUEST,
                    "Gecersiz marketplace code. MG veya YS olmalidir.",
                    "invalid_marketplace"
            );
        }
        return ResponseEntity.ok(
                dailyProductDetailsTriggerService.triggerForToday(marketplace, "MANUAL_API")
        );
    }
}
