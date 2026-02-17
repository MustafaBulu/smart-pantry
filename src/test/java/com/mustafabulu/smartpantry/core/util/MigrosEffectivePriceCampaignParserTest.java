package com.mustafabulu.smartpantry.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MigrosEffectivePriceCampaignParserTest {

    @Test
    void parsePayRefundCampaign() {
        MigrosEffectivePriceCampaignParser.EffectivePriceCampaign parsed =
                MigrosEffectivePriceCampaignParser.parse("2 Öde 1'i Money Hediye");

        assertNotNull(parsed);
        assertEquals(2, parsed.buyQuantity());
        assertEquals(1, parsed.payQuantity());
    }

    @Test
    void returnsNullForNonMatchingText() {
        assertNull(MigrosEffectivePriceCampaignParser.parse("50 TL Sepette 19,95 TL"));
    }
}
