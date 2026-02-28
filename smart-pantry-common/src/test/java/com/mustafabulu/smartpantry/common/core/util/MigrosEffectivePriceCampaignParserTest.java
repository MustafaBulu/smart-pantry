package com.mustafabulu.smartpantry.common.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MigrosEffectivePriceCampaignParserTest {

    @ParameterizedTest
    @MethodSource("matchingCampaignCases")
    void parsesMatchingCampaigns(String input, int expectedBuyQuantity, int expectedPayQuantity) {
        MigrosEffectivePriceCampaignParser.EffectivePriceCampaign parsed =
                MigrosEffectivePriceCampaignParser.parse(input);

        assertNotNull(parsed);
        assertEquals(expectedBuyQuantity, parsed.buyQuantity());
        assertEquals(expectedPayQuantity, parsed.payQuantity());
    }

    @Test
    void returnsNullForNonMatchingText() {
        assertNull(MigrosEffectivePriceCampaignParser.parse("50 TL Sepette 19,95 TL"));
    }

    private static Stream<Arguments> matchingCampaignCases() {
        return Stream.of(
                Arguments.of("2 Öde 1'i Money Hediye", 2, 1),
                Arguments.of("2 al 1 öde", 2, 1),
                Arguments.of("2.si %50 indirimli", 4, 3),
                Arguments.of("2. yuzde 50 indirimli", 4, 3),
                Arguments.of("2. urunde %50 indirim", 4, 3)
        );
    }
}
