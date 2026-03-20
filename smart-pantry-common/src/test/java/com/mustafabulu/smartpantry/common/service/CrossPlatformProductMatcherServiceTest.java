package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductCandidateResponse;
import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductMatchPairResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossPlatformProductMatcherServiceTest {

    private final CrossPlatformProductMatcherService matcherService = new CrossPlatformProductMatcherService();

    @Test
    void buildMarketplacePairsMatchesHighSimilarityProducts() {
        MarketplaceProductCandidateResponse ys = new MarketplaceProductCandidateResponse(
                "YS",
                "ys-1",
                "Sek Sut Laktozsuz 1 L",
                "Sek",
                "https://cdn.test/images/sek-sut.jpg",
                new BigDecimal("43.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        MarketplaceProductCandidateResponse mg = new MarketplaceProductCandidateResponse(
                "MG",
                "mg-1",
                "Sek Laktozsuz Sut 1 Lt",
                "Sek",
                "https://cdn.test/images/sek-sut.jpg",
                new BigDecimal("44.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        List<MarketplaceProductMatchPairResponse> pairs =
                matcherService.buildMarketplacePairs(List.of(ys), List.of(mg), 0.76d);

        assertEquals(1, pairs.size());
        assertEquals("ys-1", pairs.getFirst().ys().externalId());
        assertEquals("mg-1", pairs.getFirst().mg().externalId());
        assertTrue(pairs.getFirst().score().score() >= 0.76d);
    }

    @Test
    void buildMarketplacePairsRejectsDifferentFlavorProfiles() {
        MarketplaceProductCandidateResponse ys = new MarketplaceProductCandidateResponse(
                "YS",
                "ys-1",
                "Proteinli Sut Cilekli 500 ml",
                "Sek",
                "https://cdn.test/images/a.jpg",
                new BigDecimal("41.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        MarketplaceProductCandidateResponse mg = new MarketplaceProductCandidateResponse(
                "MG",
                "mg-1",
                "Proteinli Sut Muzlu 500 ml",
                "Sek",
                "https://cdn.test/images/b.jpg",
                new BigDecimal("42.10"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        List<MarketplaceProductMatchPairResponse> pairs =
                matcherService.buildMarketplacePairs(List.of(ys), List.of(mg), 0.5d);

        assertTrue(pairs.isEmpty());
    }

    @Test
    void buildMarketplacePairsMatchesEquivalentKgAndGramFieldAmounts() {
        MarketplaceProductCandidateResponse ys = new MarketplaceProductCandidateResponse(
                "YS",
                "ys-1",
                "Sek Sut",
                "Sek",
                "https://cdn.test/images/sek-sut.jpg",
                new BigDecimal("49.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "kg",
                1,
                null
        );
        MarketplaceProductCandidateResponse mg = new MarketplaceProductCandidateResponse(
                "MG",
                "mg-1",
                "Sek Sut",
                "Sek",
                "https://cdn.test/images/sek-sut.jpg",
                new BigDecimal("50.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "g",
                1000,
                null
        );

        List<MarketplaceProductMatchPairResponse> pairs =
                matcherService.buildMarketplacePairs(List.of(ys), List.of(mg), 0.7d);

        assertEquals(1, pairs.size());
        assertEquals("ys-1", pairs.getFirst().ys().externalId());
        assertEquals("mg-1", pairs.getFirst().mg().externalId());
    }

    @Test
    void buildMarketplacePairsMatchesBrandFamilyVariants() {
        MarketplaceProductCandidateResponse ys = new MarketplaceProductCandidateResponse(
                "YS",
                "ys-coca",
                "Coca Kola 1 L",
                "Coca",
                "https://cdn.test/images/coca.jpg",
                new BigDecimal("49.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "ml",
                1000,
                null
        );
        MarketplaceProductCandidateResponse mg = new MarketplaceProductCandidateResponse(
                "MG",
                "mg-cocacola",
                "Coca-Cola Kola 1 L",
                "Coca-Cola",
                "https://cdn.test/images/coca.jpg",
                new BigDecimal("51.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "l",
                1,
                null
        );

        List<MarketplaceProductMatchPairResponse> pairs =
                matcherService.buildMarketplacePairs(List.of(ys), List.of(mg), 0.7d);

        assertEquals(1, pairs.size());
        assertEquals("ys-coca", pairs.getFirst().ys().externalId());
        assertEquals("mg-cocacola", pairs.getFirst().mg().externalId());
        assertTrue(pairs.getFirst().score().brandScore() >= 0.85d);
    }

    @Test
    void buildMarketplacePairsMatchesMultiPackAndSinglePackWhenBrandAndTotalAmountSame() {
        MarketplaceProductCandidateResponse ys = new MarketplaceProductCandidateResponse(
                "YS",
                "ys-6pack",
                "Coca Kola 6 x 250 ml",
                "Coca",
                "https://cdn.test/images/coca-6pack.jpg",
                new BigDecimal("99.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "ml",
                1500,
                6
        );
        MarketplaceProductCandidateResponse mg = new MarketplaceProductCandidateResponse(
                "MG",
                "mg-single",
                "Coca-Cola Kola 1.5 L",
                "Coca-Cola",
                "https://cdn.test/images/coca-single.jpg",
                new BigDecimal("94.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "ml",
                1500,
                null
        );

        List<MarketplaceProductMatchPairResponse> pairs =
                matcherService.buildMarketplacePairs(List.of(ys), List.of(mg), 0.4d);

        assertEquals(1, pairs.size());
        assertEquals("ys-6pack", pairs.getFirst().ys().externalId());
        assertEquals("mg-single", pairs.getFirst().mg().externalId());
    }

    @Test
    void buildMarketplacePairsMatchesAttachedPackSuffixWithApostrophe() {
        MarketplaceProductCandidateResponse ys = new MarketplaceProductCandidateResponse(
                "YS",
                "ys-apostrophe-pack",
                "Pinar Ayran 2'li 200 ml",
                "Pinar",
                "https://cdn.test/images/pinar-pack.jpg",
                new BigDecimal("39.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        MarketplaceProductCandidateResponse mg = new MarketplaceProductCandidateResponse(
                "MG",
                "mg-plain-pack",
                "Pinar Ayran 2li 200 ml",
                "Pinar",
                "https://cdn.test/images/pinar-pack.jpg",
                new BigDecimal("39.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        List<MarketplaceProductMatchPairResponse> pairs =
                matcherService.buildMarketplacePairs(List.of(ys), List.of(mg), 0.4d);

        assertEquals(1, pairs.size());
        assertEquals("ys-apostrophe-pack", pairs.getFirst().ys().externalId());
        assertEquals("mg-plain-pack", pairs.getFirst().mg().externalId());
    }

    @Test
    void buildMarketplacePairsMatchesAyranWhenOnlyPackagingWordsDiffer() {
        MarketplaceProductCandidateResponse ys = new MarketplaceProductCandidateResponse(
                "YS",
                "ys-ayran-1l",
                "Sutas Ayran 1 L",
                "Sutas",
                "https://cdn.test/images/sutas-ayran-1l.jpg",
                new BigDecimal("55.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "l",
                1,
                null
        );
        MarketplaceProductCandidateResponse mg = new MarketplaceProductCandidateResponse(
                "MG",
                "mg-ayran-pratik-1l",
                "Sutas Pratik Sise Ayran 1 L",
                "Sutas",
                "https://cdn.test/images/sutas-pratik-sise-ayran-1l.jpg",
                new BigDecimal("49.95"),
                null,
                null,
                null,
                null,
                null,
                null,
                "ml",
                1000,
                null
        );

        List<MarketplaceProductMatchPairResponse> pairs =
                matcherService.buildMarketplacePairs(List.of(ys), List.of(mg), 0.7d);

        assertEquals(1, pairs.size());
        assertEquals("ys-ayran-1l", pairs.getFirst().ys().externalId());
        assertEquals("mg-ayran-pratik-1l", pairs.getFirst().mg().externalId());
        assertTrue(pairs.getFirst().score().score() >= 0.7d);
    }

    @Test
    void buildMarketplacePairsMatchesTurkishCharactersForAyranPackagingVariant() {
        MarketplaceProductCandidateResponse ys = new MarketplaceProductCandidateResponse(
                "YS",
                "ys-ayran-tr-1l",
                "Sütaş Ayran 1 L",
                "Sütaş",
                "https://cdn.test/images/sutas-ayran-tr-1l.jpg",
                new BigDecimal("55.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "l",
                1,
                null
        );
        MarketplaceProductCandidateResponse mg = new MarketplaceProductCandidateResponse(
                "MG",
                "mg-ayran-pratik-sise-tr-1l",
                "Sütaş Pratik Şişe Ayran 1 L",
                "Sütaş",
                "https://cdn.test/images/sutas-pratik-sise-tr-1l.jpg",
                new BigDecimal("49.95"),
                null,
                null,
                null,
                null,
                null,
                null,
                "ml",
                1000,
                null
        );

        List<MarketplaceProductMatchPairResponse> pairs =
                matcherService.buildMarketplacePairs(List.of(ys), List.of(mg), 0.68d);

        assertEquals(1, pairs.size());
        assertEquals("ys-ayran-tr-1l", pairs.getFirst().ys().externalId());
        assertEquals("mg-ayran-pratik-sise-tr-1l", pairs.getFirst().mg().externalId());
        assertTrue(pairs.getFirst().score().score() >= 0.68d);
    }

    @Test
    void buildMarketplacePairsKeepsOneToOneMappingForAlternativePackaging() {
        MarketplaceProductCandidateResponse ys = new MarketplaceProductCandidateResponse(
                "YS",
                "ys-ayran-1l",
                "Sütaş Ayran 1 L",
                "Sütaş",
                "https://cdn.test/images/ys-ayran-1l.jpg",
                new BigDecimal("55.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "l",
                1,
                null
        );

        MarketplaceProductCandidateResponse mgClassic = new MarketplaceProductCandidateResponse(
                "MG",
                "mg-ayran-1l",
                "Sütaş Ayran 1 L",
                "Sütaş",
                "https://cdn.test/images/ys-ayran-1l.jpg",
                new BigDecimal("54.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "ml",
                1000,
                null
        );

        MarketplaceProductCandidateResponse mgBottle = new MarketplaceProductCandidateResponse(
                "MG",
                "mg-ayran-pratik-1l",
                "Sütaş Pratik Şişe Ayran 1 L",
                "Sütaş",
                "https://cdn.test/images/mg-ayran-pratik-1l.jpg",
                new BigDecimal("49.95"),
                null,
                null,
                null,
                null,
                null,
                null,
                "ml",
                1000,
                null
        );

        List<MarketplaceProductMatchPairResponse> pairs =
                matcherService.buildMarketplacePairs(List.of(ys), List.of(mgClassic, mgBottle), 0.68d);

        assertEquals(1, pairs.size());
        assertEquals("ys-ayran-1l", pairs.getFirst().ys().externalId());
        assertEquals("mg-ayran-1l", pairs.getFirst().mg().externalId());
    }

    @Test
    void buildMarketplacePairsAllowsAmbiguousGroupWhenBrandIsStrongEvenIfImageSimilarityIsLow() {
        MarketplaceProductCandidateResponse ys = new MarketplaceProductCandidateResponse(
                "YS",
                "ys-sutas-1l",
                "Sütaş Ayran 1 L",
                "Sütaş",
                "https://cdn.test/images/ys-a.jpg",
                new BigDecimal("55.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "ml",
                1000,
                null
        );
        MarketplaceProductCandidateResponse mgA = new MarketplaceProductCandidateResponse(
                "MG",
                "mg-sutas-a",
                "Sütaş Ayran 1 L",
                "Sütaş",
                "https://cdn.test/images/mg-a.jpg",
                new BigDecimal("49.95"),
                null,
                null,
                null,
                null,
                null,
                null,
                "g",
                1000,
                null
        );
        MarketplaceProductCandidateResponse mgB = new MarketplaceProductCandidateResponse(
                "MG",
                "mg-sutas-b",
                "Sütaş Pratik Şişe Ayran 1 L",
                "Sütaş",
                "https://cdn.test/images/mg-b.jpg",
                new BigDecimal("49.95"),
                null,
                null,
                null,
                null,
                null,
                null,
                "g",
                1000,
                null
        );

        List<MarketplaceProductMatchPairResponse> pairs =
                matcherService.buildMarketplacePairs(List.of(ys), List.of(mgA, mgB), 0.68d);

        assertFalse(pairs.isEmpty());
    }

    @Test
    void buildMarketplacePairsMatchesWhenOneSideUsesGramOtherSideUsesMilliliter() {
        MarketplaceProductCandidateResponse ys = new MarketplaceProductCandidateResponse(
                "YS",
                "ys-sutas-1l",
                "Sütaş Ayran 1 L",
                "Sütaş",
                "https://cdn.test/images/ys-sutas-1l.jpg",
                new BigDecimal("55.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "ml",
                1000,
                null
        );
        MarketplaceProductCandidateResponse mg = new MarketplaceProductCandidateResponse(
                "MG",
                "mg-sutas-1l",
                "Sütaş Pratik Şişe Ayran 1 L",
                "Sütaş",
                "https://cdn.test/images/mg-sutas-1l.jpg",
                new BigDecimal("49.95"),
                null,
                null,
                null,
                null,
                null,
                null,
                "g",
                1000,
                null
        );

        List<MarketplaceProductMatchPairResponse> pairs =
                matcherService.buildMarketplacePairs(List.of(ys), List.of(mg), 0.68d);

        assertEquals(1, pairs.size());
        assertEquals("ys-sutas-1l", pairs.getFirst().ys().externalId());
        assertEquals("mg-sutas-1l", pairs.getFirst().mg().externalId());
    }

    @Test
    void buildMarketplacePairsRejectsSameBrandAndQuantityWhenCoreNamesDiffer() {
        MarketplaceProductCandidateResponse ys = new MarketplaceProductCandidateResponse(
                "YS",
                "ys-sut-1l",
                "Sutas %2,5 Yagli Sut 1 L",
                "Sutas",
                "https://cdn.test/images/ys-sut-1l.jpg",
                new BigDecimal("45.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "ml",
                1000,
                null
        );
        MarketplaceProductCandidateResponse mg = new MarketplaceProductCandidateResponse(
                "MG",
                "mg-latte-1l",
                "Sutas Latte 1 L",
                "Sutas",
                "https://cdn.test/images/mg-latte-1l.jpg",
                new BigDecimal("49.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "ml",
                1000,
                null
        );

        List<MarketplaceProductMatchPairResponse> pairs =
                matcherService.buildMarketplacePairs(List.of(ys), List.of(mg), 0.68d);

        assertTrue(pairs.isEmpty());
    }

    @Test
    void buildMarketplacePairsRejectsWhenInferredTypesDiffer() {
        MarketplaceProductCandidateResponse ys = new MarketplaceProductCandidateResponse(
                "YS",
                "ys-1",
                "Sutas Yogurt 1000 g",
                "Sutas",
                "https://cdn.test/images/ys-yogurt.jpg",
                new BigDecimal("59.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "g",
                1000,
                null
        );
        MarketplaceProductCandidateResponse mg = new MarketplaceProductCandidateResponse(
                "MG",
                "mg-1",
                "Sutas Peynir 1000 g",
                "Sutas",
                "https://cdn.test/images/mg-peynir.jpg",
                new BigDecimal("62.90"),
                null,
                null,
                null,
                null,
                null,
                null,
                "g",
                1000,
                null
        );

        List<MarketplaceProductMatchPairResponse> pairs =
                matcherService.buildMarketplacePairs(List.of(ys), List.of(mg), 0.68d);

        assertTrue(pairs.isEmpty());
    }
}
