package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;

public interface PlatformProductDetailsService {

    void recordDailyDetails(String categoryName);

    default boolean supportsMarketplace(Marketplace marketplace) {
        return true;
    }
}
