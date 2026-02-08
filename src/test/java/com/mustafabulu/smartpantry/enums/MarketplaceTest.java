package com.mustafabulu.smartpantry.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MarketplaceTest {

    @Test
    void fromCodeReturnsNullWhenBlankOrUnknown() {
        assertNull(Marketplace.fromCode(null));
        assertNull(Marketplace.fromCode(""));
        assertNull(Marketplace.fromCode("  "));
        assertNull(Marketplace.fromCode("XX"));
    }

    @Test
    void fromCodeMatchesCaseInsensitive() {
        assertEquals(Marketplace.YS, Marketplace.fromCode("ys"));
        assertEquals(Marketplace.MG, Marketplace.fromCode(" Mg "));
    }
}