package com.mustafabulu.smartpantry.common.controller.impl;

import com.mustafabulu.smartpantry.common.dto.response.CategoryPriceSummaryResponse;
import com.mustafabulu.smartpantry.common.dto.response.PriceHistoryResponse;
import com.mustafabulu.smartpantry.common.service.MarketplaceRequestResolver;
import com.mustafabulu.smartpantry.common.service.PriceHistoryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriceHistoryControllerImplTest {

    @Test
    void getProductPricesReturnsList() {
        PriceHistoryService service = mock(PriceHistoryService.class);
        MarketplaceRequestResolver resolver = mock(MarketplaceRequestResolver.class);
        PriceHistoryControllerImpl controller = new PriceHistoryControllerImpl(service, resolver);
        when(resolver.resolveOptional("YS")).thenReturn("YS");
        when(service.getProductPrices(1L, "YS", null, null, false, false))
                .thenReturn(List.of(new PriceHistoryResponse(1L, 1L, "Chips", "YS", BigDecimal.ONE, BigDecimal.valueOf(75.0), "Yuksek", LocalDate.now())));

        var response = controller.getProductPrices(1L, "YS", false, false);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void getCategoryPricesReturnsList() {
        PriceHistoryService service = mock(PriceHistoryService.class);
        MarketplaceRequestResolver resolver = mock(MarketplaceRequestResolver.class);
        PriceHistoryControllerImpl controller = new PriceHistoryControllerImpl(service, resolver);
        when(resolver.resolveOptional("YS")).thenReturn("YS");
        when(service.getCategorySummary("Snacks", "YS", null, null))
                .thenReturn(List.of(new CategoryPriceSummaryResponse(1L, "Chips", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, LocalDate.now())));

        var response = controller.getCategoryPrices("Snacks", "YS");

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }
}
