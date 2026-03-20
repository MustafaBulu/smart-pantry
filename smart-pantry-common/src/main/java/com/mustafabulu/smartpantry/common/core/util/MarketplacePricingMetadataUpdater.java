package com.mustafabulu.smartpantry.common.core.util;

import com.mustafabulu.smartpantry.common.model.MarketplaceProduct;

import java.math.BigDecimal;
import java.util.Objects;

public final class MarketplacePricingMetadataUpdater {

    private MarketplacePricingMetadataUpdater() {
    }

    public static boolean applyCampaignAndDiscountMetadata(
            MarketplaceProduct marketplaceProduct,
            BigDecimal basketDiscountThreshold,
            BigDecimal basketDiscountPrice,
            Integer campaignBuyQuantity,
            Integer campaignPayQuantity,
            BigDecimal effectivePrice
    ) {
        boolean updated = false;
        updated |= setIfDifferent(
                basketDiscountThreshold,
                marketplaceProduct.getBasketDiscountThreshold(),
                marketplaceProduct::setBasketDiscountThreshold
        );
        updated |= setIfDifferent(
                basketDiscountPrice,
                marketplaceProduct.getBasketDiscountPrice(),
                marketplaceProduct::setBasketDiscountPrice
        );
        updated |= setIfDifferent(
                campaignBuyQuantity,
                marketplaceProduct.getCampaignBuyQuantity(),
                marketplaceProduct::setCampaignBuyQuantity
        );
        updated |= setIfDifferent(
                campaignPayQuantity,
                marketplaceProduct.getCampaignPayQuantity(),
                marketplaceProduct::setCampaignPayQuantity
        );
        updated |= setIfDifferent(
                effectivePrice,
                marketplaceProduct.getEffectivePrice(),
                marketplaceProduct::setEffectivePrice
        );
        return updated;
    }

    private static <T> boolean setIfDifferent(T incoming, T current, java.util.function.Consumer<T> setter) {
        if (incoming == null || Objects.equals(incoming, current)) {
            return false;
        }
        setter.accept(incoming);
        return true;
    }
}
