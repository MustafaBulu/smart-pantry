package com.mustafabulu.smartpantry.common.core.util;

import com.mustafabulu.smartpantry.common.model.MarketplaceProduct;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductUrlResolverTest {

    @Test
    void resolvesExplicitUrlWhenValid() {
        MarketplaceProduct product = new MarketplaceProduct();
        product.setProductUrl("https://example.com/item");
        product.setExternalId("123");

        assertEquals("https://example.com/item",
                ProductUrlResolver.resolveProductUrl(product, "https://prefix/", ""));
    }

    @Test
    void ignoresInvalidExplicitUrl() {
        MarketplaceProduct product = new MarketplaceProduct();
        product.setProductUrl("${MISSING}");
        product.setExternalId("123");

        assertEquals("https://prefix/123",
                ProductUrlResolver.resolveProductUrl(product, "https://prefix/", ""));
    }

    @Test
    void returnsNullWhenExternalIdMissing() {
        MarketplaceProduct product = new MarketplaceProduct();
        product.setProductUrl(null);
        product.setExternalId(" ");

        assertNull(ProductUrlResolver.resolveProductUrl(product, "https://prefix/", ""));
    }

    @Test
    void appendsSuffixWhenProvided() {
        MarketplaceProduct product = new MarketplaceProduct();
        product.setExternalId("ABC");

        assertEquals("https://prefix/ABC?x=1",
                ProductUrlResolver.resolveProductUrl(product, "https://prefix/", "?x=1"));
    }
}