package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulerServiceTest {

    @Test
    void runDailyCallsServiceForEachCategory() {
        DailyProductDetailsService detailsService = mock(DailyProductDetailsService.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        SchedulerService schedulerService = new SchedulerService(detailsService, categoryRepository);

        Category category = new Category();
        category.setName("Snacks");
        Category blank = new Category();
        blank.setName("  ");
        when(categoryRepository.findAll()).thenReturn(List.of(category, blank));

        schedulerService.runDaily();

        verify(detailsService).recordDailyDetails("Snacks");
    }
}
