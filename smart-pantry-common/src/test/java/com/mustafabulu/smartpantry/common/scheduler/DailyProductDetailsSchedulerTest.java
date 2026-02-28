package com.mustafabulu.smartpantry.common.scheduler;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.service.DailyProductDetailsTriggerService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DailyProductDetailsSchedulerTest {

    @Test
    void recordDailyDetailsForMigrosInvokesServiceForEachCategory() {
        DailyProductDetailsTriggerService triggerService = mock(DailyProductDetailsTriggerService.class);
        DailyProductDetailsScheduler scheduler = new DailyProductDetailsScheduler(triggerService, true, true);

        scheduler.recordDailyDetailsForMigros();

        verify(triggerService).triggerForToday(Marketplace.MG, "SCHEDULER");
    }

    @Test
    void recordDailyDetailsForYemeksepetiInvokesServiceForEachCategory() {
        DailyProductDetailsTriggerService triggerService = mock(DailyProductDetailsTriggerService.class);
        DailyProductDetailsScheduler scheduler = new DailyProductDetailsScheduler(triggerService, true, true);

        scheduler.recordDailyDetailsForYemeksepeti();

        verify(triggerService).triggerForToday(Marketplace.YS, "SCHEDULER");
    }
}
