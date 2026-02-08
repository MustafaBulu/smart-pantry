package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
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
}
