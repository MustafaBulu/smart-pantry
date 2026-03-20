package com.mustafabulu.smartpantry.common.core.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MigrosBasketDiscountParserTest {

    @Test
    void parseReturnsDiscountForValidTag() {
        MigrosBasketDiscountParser.BasketDiscount discount =
                MigrosBasketDiscountParser.parse("50 TL Sepette 17,95 TL");

        assertEquals(0, new BigDecimal("50").compareTo(discount.threshold()));
        assertEquals(0, new BigDecimal("17.95").compareTo(discount.discountedPrice()));
    }

    @Test
    void parseReturnsNullForInvalidTag() {
        assertNull(MigrosBasketDiscountParser.parse("indirim yok"));
        assertNull(MigrosBasketDiscountParser.parse(null));
    }
}
