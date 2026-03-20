package com.mustafabulu.smartpantry.common.controller.impl;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.request.CatalogCategoryRangeImportRequest;
import com.mustafabulu.smartpantry.common.dto.request.CatalogImportRequest;
import com.mustafabulu.smartpantry.common.dto.response.BasketMinimumSettingsResponse;
import com.mustafabulu.smartpantry.common.dto.response.DailyProductDetailsTriggerResponse;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.service.BasketSettingsService;
import com.mustafabulu.smartpantry.common.service.MarketplaceCatalogCategoryRangeImportResolverService;
import com.mustafabulu.smartpantry.common.service.MarketplaceCatalogCategoryRangeImportService;
import com.mustafabulu.smartpantry.common.service.DailyProductDetailsTriggerService;
import com.mustafabulu.smartpantry.common.service.MarketplaceCategorySeedResolverService;
import com.mustafabulu.smartpantry.common.service.MarketplaceProductService;
import com.mustafabulu.smartpantry.common.service.MarketplaceRequestResolver;
import com.mustafabulu.smartpantry.common.service.SettingsAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settings")
public class SettingsControllerImpl {

    private final BasketSettingsService basketSettingsService;
    private final DailyProductDetailsTriggerService dailyProductDetailsTriggerService;
    private final MarketplaceProductService marketplaceProductService;
    private final MarketplaceCatalogCategoryRangeImportResolverService marketplaceCatalogCategoryRangeImportResolverService;
    private final MarketplaceCategorySeedResolverService marketplaceCategorySeedResolverService;
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

    @GetMapping("/catalog-seeds")
    public ResponseEntity<List<String>> listCatalogSeeds(
            @RequestParam String marketplaceCode,
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
                marketplaceCategorySeedResolverService.listSeedCategoryKeys(marketplace)
        );
    }

    @PostMapping("/catalog/import-from-urls")
    public ResponseEntity<MarketplaceProductService.CatalogSyncResult> importCatalogFromUrls(
            @RequestBody CatalogImportRequest request,
            @RequestHeader(value = SettingsAccessGuard.SETTINGS_API_KEY_HEADER, required = false) String settingsApiKey
    ) {
        settingsAccessGuard.assertAuthorized(settingsApiKey);
        Marketplace marketplace = Marketplace.fromCode(
                marketplaceRequestResolver.resolveRequired(request.marketplaceCode())
        );
        if (marketplace == null) {
            throw new SPException(
                    BAD_REQUEST,
                    "Gecersiz marketplace code. MG veya YS olmalidir.",
                    "invalid_marketplace"
            );
        }
        return ResponseEntity.ok(
                marketplaceProductService.syncCatalogFromUrls(marketplace, request.urls())
        );
    }

    @PostMapping("/catalog/import-category-range")
    public ResponseEntity<MarketplaceCatalogCategoryRangeImportService.CatalogCategoryRangeImportResult> importCatalogByCategoryRange(
            @RequestBody CatalogCategoryRangeImportRequest request,
            @RequestHeader(value = SettingsAccessGuard.SETTINGS_API_KEY_HEADER, required = false) String settingsApiKey
    ) {
        settingsAccessGuard.assertAuthorized(settingsApiKey);
        Marketplace marketplace = Marketplace.fromCode(
                marketplaceRequestResolver.resolveRequired(request.marketplaceCode())
        );
        if (marketplace == null) {
            throw new SPException(
                    BAD_REQUEST,
                    "Gecersiz marketplace code. MG veya YS olmalidir.",
                    "invalid_marketplace"
            );
        }
        int start = request.startCategoryId() == null ? 2 : request.startCategoryId();
        int end = request.endCategoryId() == null ? 10 : request.endCategoryId();
        if (start <= 0 || end <= 0 || end < start) {
            throw new SPException(
                    BAD_REQUEST,
                    "Kategori araligi gecersiz.",
                    "invalid_category_range"
            );
        }
        return ResponseEntity.ok(
                marketplaceCatalogCategoryRangeImportResolverService.importFromCategoryRange(
                        marketplace,
                        request.sourceUrl(),
                        start,
                        end
                )
        );
    }
}
