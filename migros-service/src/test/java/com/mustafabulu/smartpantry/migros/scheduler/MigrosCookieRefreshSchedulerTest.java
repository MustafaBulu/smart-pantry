package com.mustafabulu.smartpantry.migros.scheduler;

import com.mustafabulu.smartpantry.migros.service.MigrosCookieSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MigrosCookieRefreshSchedulerTest {

    @Test
    void refreshMigrosCookieCacheRunsOnlyWhenEnabled() {
        MigrosCookieSessionService service = mock(MigrosCookieSessionService.class);
        MigrosCookieRefreshScheduler scheduler = new MigrosCookieRefreshScheduler(service);

        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", true);
        scheduler.refreshMigrosCookieCache();
        verify(service).refreshFromSelenium();

        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", false);
        scheduler.refreshMigrosCookieCache();
        verify(service).refreshFromSelenium();
    }
}
