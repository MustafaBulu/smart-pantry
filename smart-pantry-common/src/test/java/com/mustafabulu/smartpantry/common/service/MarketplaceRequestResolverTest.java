package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketplaceRequestResolverTest {

    @Test
    void resolveOptionalReturnsProvidedMarketplaceWhenEnabled() {
        MarketplaceRequestResolver resolver = new MarketplaceRequestResolver(true, true);

        assertEquals("MG", resolver.resolveOptional("mg"));
    }

    @Test
    void resolveOptionalReturnsOnlyEnabledMarketplaceWhenSingleServiceActive() {
        MarketplaceRequestResolver resolver = new MarketplaceRequestResolver(false, true);

        assertEquals("YS", resolver.resolveOptional(null));
    }

    @Test
    void resolveOptionalReturnsNullWhenBothEnabledAndNoInput() {
        MarketplaceRequestResolver resolver = new MarketplaceRequestResolver(true, true);

        assertNull(resolver.resolveOptional(null));
    }

    @Test
    void resolveOptionalRejectsDisabledMarketplace() {
        MarketplaceRequestResolver resolver = new MarketplaceRequestResolver(true, false);

        assertThrows(SPException.class, () -> resolver.resolveOptional("YS"));
    }

    @Test
    void resolveRequiredRejectsMissingInputWhenAmbiguous() {
        MarketplaceRequestResolver resolver = new MarketplaceRequestResolver(true, true);

        assertThrows(SPException.class, () -> resolver.resolveRequired(null));
    }
}
