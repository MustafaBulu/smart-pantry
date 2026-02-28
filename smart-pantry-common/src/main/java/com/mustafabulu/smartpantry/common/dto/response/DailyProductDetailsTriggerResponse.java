package com.mustafabulu.smartpantry.common.dto.response;

import java.time.LocalDate;

public record DailyProductDetailsTriggerResponse(
        String marketplaceCode,
        LocalDate runDate,
        boolean executed,
        String message
) {
}
