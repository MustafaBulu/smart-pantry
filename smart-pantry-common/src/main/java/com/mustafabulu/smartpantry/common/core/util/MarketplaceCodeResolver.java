package com.mustafabulu.smartpantry.common.core.util;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import org.springframework.http.HttpStatus;

public final class MarketplaceCodeResolver {

    private MarketplaceCodeResolver() {
    }

    public static Marketplace resolveNullable(String marketplaceCode) {
        if (marketplaceCode == null || marketplaceCode.isBlank()) {
            return null;
        }
        Marketplace marketplace = Marketplace.fromCode(marketplaceCode);
        if (marketplace == null) {
            throw new SPException(
                    HttpStatus.BAD_REQUEST,
                    ResponseMessages.INVALID_MARKETPLACE_CODE,
                    ResponseMessages.INVALID_MARKETPLACE_CODE
            );
        }
        return marketplace;
    }
}
