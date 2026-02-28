package com.mustafabulu.smartpantry.common.core.util;

import com.mustafabulu.smartpantry.common.model.MarketplaceProduct;

public final class ProductUrlResolver {

    private ProductUrlResolver() {
    }

    public static String resolveProductUrl(
            MarketplaceProduct marketplaceProduct,
            String urlPrefix,
            String urlSuffix
    ) {
        String explicitUrl = marketplaceProduct.getProductUrl();
        if (explicitUrl != null && !explicitUrl.isBlank() && isValidUrl(explicitUrl)) {
            return explicitUrl;
        }
        String externalId = marketplaceProduct.getExternalId();
        if (externalId == null || externalId.isBlank()) {
            return null;
        }
        return urlPrefix + externalId + (urlSuffix == null ? "" : urlSuffix);
    }

    private static boolean isValidUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.contains("${")) {
            return false;
        }
        return trimmed.startsWith("https://");
    }
}
