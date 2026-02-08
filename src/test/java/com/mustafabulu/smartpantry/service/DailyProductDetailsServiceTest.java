package com.mustafabulu.smartpantry.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
}