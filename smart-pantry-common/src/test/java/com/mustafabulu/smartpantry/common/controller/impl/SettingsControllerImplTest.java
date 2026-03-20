package com.mustafabulu.smartpantry.common.controller.impl;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.request.CatalogCategoryRangeImportRequest;
import com.mustafabulu.smartpantry.common.dto.request.CatalogImportRequest;
import com.mustafabulu.smartpantry.common.dto.response.BasketMinimumSettingsResponse;
import com.mustafabulu.smartpantry.common.dto.response.DailyProductDetailsTriggerResponse;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.service.BasketSettingsService;
import com.mustafabulu.smartpantry.common.service.DailyProductDetailsTriggerService;
import com.mustafabulu.smartpantry.common.service.MarketplaceCatalogCategoryRangeImportResolverService;
import com.mustafabulu.smartpantry.common.service.MarketplaceCatalogCategoryRangeImportService;
import com.mustafabulu.smartpantry.common.service.MarketplaceCategorySeedResolverService;
import com.mustafabulu.smartpantry.common.service.MarketplaceProductService;
import com.mustafabulu.smartpantry.common.service.MarketplaceRequestResolver;
import com.mustafabulu.smartpantry.common.service.SettingsAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsControllerImplTest {

    @Mock
    private BasketSettingsService basketSettingsService;
    @Mock
    private DailyProductDetailsTriggerService dailyProductDetailsTriggerService;
    @Mock
    private MarketplaceProductService marketplaceProductService;
    @Mock
    private MarketplaceCatalogCategoryRangeImportResolverService rangeImportResolverService;
    @Mock
    private MarketplaceCategorySeedResolverService seedResolverService;
    @Mock
    private MarketplaceRequestResolver marketplaceRequestResolver;
    @Mock
    private SettingsAccessGuard settingsAccessGuard;

    private SettingsControllerImpl controller;

    @BeforeEach
    void setUp() {
        controller = new SettingsControllerImpl(
                basketSettingsService,
                dailyProductDetailsTriggerService,
                marketplaceProductService,
                rangeImportResolverService,
                seedResolverService,
                marketplaceRequestResolver,
                settingsAccessGuard
        );
    }

    @Test
    void getBasketMinimumSettingsReturnsServiceValue() {
        BasketMinimumSettingsResponse response = new BasketMinimumSettingsResponse(
                new BigDecimal("150.00"),
                new BigDecimal("250.00")
        );
        when(basketSettingsService.getMinimumBasketSettings()).thenReturn(response);

        BasketMinimumSettingsResponse body = controller.getBasketMinimumSettings().getBody();

        assertEquals(response, body);
    }

    @Test
    void triggerDailyProductDetailsReturnsTriggerResponse() {
        DailyProductDetailsTriggerResponse triggerResponse = new DailyProductDetailsTriggerResponse(
                "MG",
                LocalDate.of(2026, 3, 20),
                true,
                "Triggered"
        );
        when(marketplaceRequestResolver.resolveRequired("mg")).thenReturn("MG");
        when(dailyProductDetailsTriggerService.triggerForToday(Marketplace.MG, "MANUAL_API"))
                .thenReturn(triggerResponse);

        DailyProductDetailsTriggerResponse body = controller.triggerDailyProductDetails("mg", "secret").getBody();

        assertEquals(triggerResponse, body);
        verify(settingsAccessGuard).assertAuthorized("secret");
    }

    @Test
    void listCatalogSeedsThrowsWhenMarketplaceInvalid() {
        when(marketplaceRequestResolver.resolveRequired("xx")).thenReturn("XX");

        SPException exception = assertThrows(
                SPException.class,
                () -> controller.listCatalogSeeds("xx", "secret")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void importCatalogFromUrlsDelegatesToMarketplaceService() {
        CatalogImportRequest request = new CatalogImportRequest("YS", List.of("https://example/catalog"));
        MarketplaceProductService.CatalogSyncResult result =
                new MarketplaceProductService.CatalogSyncResult("YS", 1, 12, 3, 2);
        when(marketplaceRequestResolver.resolveRequired("YS")).thenReturn("YS");
        when(marketplaceProductService.syncCatalogFromUrls(Marketplace.YS, request.urls())).thenReturn(result);

        MarketplaceProductService.CatalogSyncResult body =
                controller.importCatalogFromUrls(request, "secret").getBody();

        assertEquals(result, body);
        verify(settingsAccessGuard).assertAuthorized("secret");
    }

    @Test
    void importCatalogByCategoryRangeAppliesDefaults() {
        CatalogCategoryRangeImportRequest request = new CatalogCategoryRangeImportRequest(
                "MG",
                "https://example/catalog",
                null,
                null
        );
        MarketplaceCatalogCategoryRangeImportService.CatalogCategoryRangeImportResult result =
                new MarketplaceCatalogCategoryRangeImportService.CatalogCategoryRangeImportResult(
                        "MG",
                        9,
                        10,
                        120,
                        100,
                        40,
                        60
                );
        when(marketplaceRequestResolver.resolveRequired("MG")).thenReturn("MG");
        when(rangeImportResolverService.importFromCategoryRange(Marketplace.MG, "https://example/catalog", 2, 10))
                .thenReturn(result);

        MarketplaceCatalogCategoryRangeImportService.CatalogCategoryRangeImportResult body =
                controller.importCatalogByCategoryRange(request, "secret").getBody();

        assertEquals(result, body);
    }

    @Test
    void importCatalogByCategoryRangeRejectsInvalidRange() {
        CatalogCategoryRangeImportRequest request = new CatalogCategoryRangeImportRequest(
                "MG",
                "https://example/catalog",
                10,
                5
        );
        when(marketplaceRequestResolver.resolveRequired("MG")).thenReturn("MG");

        SPException exception = assertThrows(
                SPException.class,
                () -> controller.importCatalogByCategoryRange(request, "secret")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
