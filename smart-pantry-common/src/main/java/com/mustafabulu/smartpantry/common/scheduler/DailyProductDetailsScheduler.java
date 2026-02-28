package com.mustafabulu.smartpantry.common.scheduler;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.service.DailyProductDetailsTriggerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyProductDetailsScheduler {

    private final DailyProductDetailsTriggerService dailyProductDetailsTriggerService;
    private final boolean migrosSchedulerEnabled;
    private final boolean yemeksepetiSchedulerEnabled;

    public DailyProductDetailsScheduler(
            DailyProductDetailsTriggerService dailyProductDetailsTriggerService,
            @Value("${daily.product.details.scheduler.migros.enabled:true}") boolean migrosSchedulerEnabled,
            @Value("${daily.product.details.scheduler.yemeksepeti.enabled:true}") boolean yemeksepetiSchedulerEnabled
    ) {
        this.dailyProductDetailsTriggerService = dailyProductDetailsTriggerService;
        this.migrosSchedulerEnabled = migrosSchedulerEnabled;
        this.yemeksepetiSchedulerEnabled = yemeksepetiSchedulerEnabled;
    }

    @Scheduled(
            fixedDelayString = "${daily.product.details.scheduler.migros.fixed-delay:PT1H}",
            initialDelayString = "${daily.product.details.scheduler.migros.initial-delay:PT0S}"
    )
    public void recordDailyDetailsForMigros() {
        if (!migrosSchedulerEnabled) {
            return;
        }
        dailyProductDetailsTriggerService.triggerForToday(Marketplace.MG, "SCHEDULER");
    }

    @Scheduled(
            fixedDelayString = "${daily.product.details.scheduler.yemeksepeti.fixed-delay:PT1H}",
            initialDelayString = "${daily.product.details.scheduler.yemeksepeti.initial-delay:PT10M}"
    )
    public void recordDailyDetailsForYemeksepeti() {
        if (!yemeksepetiSchedulerEnabled) {
            return;
        }
        dailyProductDetailsTriggerService.triggerForToday(Marketplace.YS, "SCHEDULER");
    }
}
