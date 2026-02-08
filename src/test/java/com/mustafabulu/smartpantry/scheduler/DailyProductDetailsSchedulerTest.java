package com.mustafabulu.smartpantry.scheduler;

import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.service.DailyProductDetailsService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyProductDetailsSchedulerTest {

    @Test
    void recordDailyDetailsInvokesServiceForEachCategory() {
        DailyProductDetailsService detailsService = mock(DailyProductDetailsService.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        DailyProductDetailsScheduler scheduler = new DailyProductDetailsScheduler(detailsService, categoryRepository);

        Category category = new Category();
        category.setName("Snacks");
        when(categoryRepository.findAll()).thenReturn(List.of(category));

        scheduler.recordDailyDetails();

        verify(detailsService).recordDailyDetails("Snacks");
    }
}