package com.mustafabulu.smartpantry.common.core.util;

import com.mustafabulu.smartpantry.common.model.MarketplaceProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplacePricingMetadataUpdaterTest {

    @Test
    void applyCampaignAndDiscountMetadataUpdatesChangedFields() {
        MarketplaceProduct product = new MarketplaceProduct();

        boolean updated = MarketplacePricingMetadataUpdater.applyCampaignAndDiscountMetadata(
                product,
                new BigDecimal("100"),
                new BigDecimal("79.90"),
                2,
                1,
                new BigDecimal("39.95")
        );

        assertTrue(updated);
        assertEquals(0, new BigDecimal("100").compareTo(product.getBasketDiscountThreshold()));
        assertEquals(0, new BigDecimal("79.90").compareTo(product.getBasketDiscountPrice()));
        assertEquals(2, product.getCampaignBuyQuantity());
        assertEquals(1, product.getCampaignPayQuantity());
        assertEquals(0, new BigDecimal("39.95").compareTo(product.getEffectivePrice()));
    }

    @Test
    void applyCampaignAndDiscountMetadataSkipsNullAndSameValues() {
        MarketplaceProduct product = new MarketplaceProduct();
        product.setBasketDiscountThreshold(new BigDecimal("100"));
        product.setBasketDiscountPrice(new BigDecimal("79.90"));
        product.setCampaignBuyQuantity(2);
        product.setCampaignPayQuantity(1);
        product.setEffectivePrice(new BigDecimal("39.95"));

        boolean updated = MarketplacePricingMetadataUpdater.applyCampaignAndDiscountMetadata(
                product,
                new BigDecimal("100"),
                new BigDecimal("79.90"),
                2,
                1,
                new BigDecimal("39.95")
        );

        assertFalse(updated);
    }
}
