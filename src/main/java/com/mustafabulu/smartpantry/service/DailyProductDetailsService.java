package com.mustafabulu.smartpantry.service;

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
}
