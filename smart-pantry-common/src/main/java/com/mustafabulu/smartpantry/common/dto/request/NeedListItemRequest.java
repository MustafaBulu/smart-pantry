package com.mustafabulu.smartpantry.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Need list item payload")
public record NeedListItemRequest(
        String key,
        String type,
        Long categoryId,
        String categoryName,
        String externalId,
        String marketplaceCode,
        String name,
        String imageUrl,
        BigDecimal price,
        BigDecimal moneyPrice,
        BigDecimal basketDiscountThreshold,
        BigDecimal basketDiscountPrice,
        Integer campaignBuyQuantity,
        Integer campaignPayQuantity,
        BigDecimal effectivePrice,
        String urgency,
        BigDecimal availabilityScore,
        Integer historyDayCount,
        String availabilityStatus,
        String opportunityLevel
) {
}
