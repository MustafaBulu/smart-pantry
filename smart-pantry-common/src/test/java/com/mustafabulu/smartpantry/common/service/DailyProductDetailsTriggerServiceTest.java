package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.dto.response.DailyProductDetailsTriggerResponse;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.model.DailyProductDetailsRun;
import com.mustafabulu.smartpantry.common.repository.CategoryRepository;
import com.mustafabulu.smartpantry.common.repository.DailyProductDetailsRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyProductDetailsTriggerServiceTest {

    @Test
    void triggerForTodayReturnsAlreadyRecordedWhenRunExists() {
        DailyProductDetailsService detailsService = mock(DailyProductDetailsService.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        DailyProductDetailsRunRepository runRepository = mock(DailyProductDetailsRunRepository.class);
        DailyProductDetailsTriggerService service =
                new DailyProductDetailsTriggerService(detailsService, categoryRepository, runRepository);

        when(runRepository.save(any(DailyProductDetailsRun.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        DailyProductDetailsTriggerResponse response = service.triggerForToday(Marketplace.MG, "MANUAL_API");

        assertFalse(response.executed());
        verify(detailsService, never()).recordDailyDetails(any(String.class), any(Marketplace.class));
        verify(runRepository).save(any(DailyProductDetailsRun.class));
    }

    @Test
    void triggerForTodayRecordsWhenRunDoesNotExist() {
        DailyProductDetailsService detailsService = mock(DailyProductDetailsService.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        DailyProductDetailsRunRepository runRepository = mock(DailyProductDetailsRunRepository.class);
        DailyProductDetailsTriggerService service =
                new DailyProductDetailsTriggerService(detailsService, categoryRepository, runRepository);

        Category category = new Category();
        category.setName("Snacks");
        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(runRepository.save(any(DailyProductDetailsRun.class)))
                .thenAnswer(invocation -> {
                    DailyProductDetailsRun run = invocation.getArgument(0);
                    run.setId(1L);
                    run.setRunDate(LocalDate.now());
                    return run;
                });

        DailyProductDetailsTriggerResponse response = service.triggerForToday(Marketplace.YS, "MANUAL_API");

        assertTrue(response.executed());
        verify(detailsService).recordDailyDetails("Snacks", Marketplace.YS);
        verify(runRepository).save(any(DailyProductDetailsRun.class));
    }
}
