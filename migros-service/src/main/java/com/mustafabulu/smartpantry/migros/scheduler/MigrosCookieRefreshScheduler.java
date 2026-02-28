package com.mustafabulu.smartpantry.migros.scheduler;

import com.mustafabulu.smartpantry.migros.service.MigrosCookieSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "marketplace.mg", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigrosCookieRefreshScheduler {

    private final MigrosCookieSessionService migrosCookieSessionService;

    @Value("${migros.cookie.refresh.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    @Scheduled(
            fixedDelayString = "${migros.cookie.refresh.scheduler.fixed-delay:PT30M}",
            initialDelayString = "${migros.cookie.refresh.scheduler.initial-delay:PT2M}"
    )
    public void refreshMigrosCookieCache() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            migrosCookieSessionService.refreshFromSelenium();
        } catch (Exception exception) {
            log.warn("Migros cookie auto refresh failed: {}", exception.getMessage());
        }
    }
}
