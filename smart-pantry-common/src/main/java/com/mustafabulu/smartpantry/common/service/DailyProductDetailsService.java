package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class DailyProductDetailsService {

    private final List<PlatformProductDetailsService> platformServices;

    public void recordDailyDetails(String categoryName) {
        for (PlatformProductDetailsService service : platformServices) {
            service.recordDailyDetails(categoryName);
        }
    }

    public void recordDailyDetails(String categoryName, Marketplace marketplace) {
        for (PlatformProductDetailsService service : platformServices) {
            if (service.supportsMarketplace(marketplace)) {
                service.recordDailyDetails(categoryName);
            }
        }
    }
}
