package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.repository.CategoryRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class SchedulerService {

    private final DailyProductDetailsService dailyProductDetailsService;
    private final CategoryRepository categoryRepository;

    public void runDaily() {
        categoryRepository.findAll().stream()
                .map(Category::getName)
                .filter(name -> name != null && !name.isBlank())
                .forEach(dailyProductDetailsService::recordDailyDetails);
    }

    public void runDailyForMarketplace(Marketplace marketplace) {
        categoryRepository.findAll().stream()
                .map(Category::getName)
                .filter(name -> name != null && !name.isBlank())
                .forEach(name -> dailyProductDetailsService.recordDailyDetails(name, marketplace));
    }
}
