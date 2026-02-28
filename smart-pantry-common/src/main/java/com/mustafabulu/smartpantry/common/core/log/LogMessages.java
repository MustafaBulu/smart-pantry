package com.mustafabulu.smartpantry.common.core.log;

public final class LogMessages {

    public static final String VALIDATION_FAILED = "{} Validation failed: {}";
    public static final String UNEXPECTED_ERROR = "Unexpected error: {}";
    public static final String MIGROS_FETCH_FAILED = "Migros product fetch failed: externalId={}, status={}";
    public static final String DAILY_PRODUCT_LOGGED =
            "Daily product logged: category={}, marketplaceCode={}, marketplaceName={}, date={}, productId={}, productName={}, externalId={}, price={}";
    public static final String EXCEPTION = "EXCEPTION";

    private LogMessages() {
    }
}
