package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.repository.CategoryRepository;
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

    @Test
    void runDailyForMarketplaceCallsMarketplaceSpecificServiceForEachCategory() {
        DailyProductDetailsService detailsService = mock(DailyProductDetailsService.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        SchedulerService schedulerService = new SchedulerService(detailsService, categoryRepository);

        Category category = new Category();
        category.setName("Snacks");
        when(categoryRepository.findAll()).thenReturn(List.of(category));

        schedulerService.runDailyForMarketplace(Marketplace.YS);

        verify(detailsService).recordDailyDetails("Snacks", Marketplace.YS);
    }
}
