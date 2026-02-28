package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyProductDetailsServiceTest {

    @Test
    void recordDailyDetailsDelegatesToPlatforms() {
        PlatformProductDetailsService first = mock(PlatformProductDetailsService.class);
        PlatformProductDetailsService second = mock(PlatformProductDetailsService.class);
        DailyProductDetailsService service = new DailyProductDetailsService(List.of(first, second));

        service.recordDailyDetails("Snacks");

        verify(first).recordDailyDetails("Snacks");
        verify(second).recordDailyDetails("Snacks");
    }

    @Test
    void recordDailyDetailsForMarketplaceDelegatesOnlyMatchingPlatforms() {
        PlatformProductDetailsService migrosService = mock(PlatformProductDetailsService.class);
        PlatformProductDetailsService yemeksepetiService = mock(PlatformProductDetailsService.class);
        when(migrosService.supportsMarketplace(Marketplace.MG)).thenReturn(true);
        when(yemeksepetiService.supportsMarketplace(Marketplace.MG)).thenReturn(false);

        DailyProductDetailsService service = new DailyProductDetailsService(List.of(migrosService, yemeksepetiService));

        service.recordDailyDetails("Snacks", Marketplace.MG);

        verify(migrosService).recordDailyDetails("Snacks");
        verify(yemeksepetiService, never()).recordDailyDetails("Snacks");
    }
}
