package com.mustafabulu.smartpantry.scheduler;

import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.service.DailyProductDetailsService;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class DailyProductDetailsScheduler {

    private final DailyProductDetailsService dailyProductDetailsService;
    private final CategoryRepository categoryRepository;

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT0S")
    public void recordDailyDetails() {
        categoryRepository.findAll().stream()
                .map(Category::getName)
                .filter(name -> name != null && !name.isBlank())
                .forEach(dailyProductDetailsService::recordDailyDetails);
    }
}
