package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.dto.response.DailyProductDetailsTriggerResponse;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.model.DailyProductDetailsRun;
import com.mustafabulu.smartpantry.common.repository.CategoryRepository;
import com.mustafabulu.smartpantry.common.repository.DailyProductDetailsRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DailyProductDetailsTriggerService {

    private final DailyProductDetailsService dailyProductDetailsService;
    private final CategoryRepository categoryRepository;
    private final DailyProductDetailsRunRepository dailyProductDetailsRunRepository;
    @Value("${marketplace.mg.enabled:true}")
    private boolean migrosEnabled = true;
    @Value("${marketplace.ys.enabled:true}")
    private boolean yemeksepetiEnabled = true;

    public DailyProductDetailsTriggerResponse triggerForToday(Marketplace marketplace, String triggerSource) {
        if (!isMarketplaceEnabled(marketplace)) {
            return new DailyProductDetailsTriggerResponse(
                    marketplace.getCode(),
                    LocalDate.now(),
                    false,
                    "Marketplace bu microservice'te aktif degil."
            );
        }
        LocalDate today = LocalDate.now();
        if (dailyProductDetailsRunRepository.findByMarketplaceAndRunDate(marketplace, today).isPresent()) {
            return new DailyProductDetailsTriggerResponse(
                    marketplace.getCode(),
                    today,
                    false,
                    "Bugun icin daha once kayit yapilmis."
            );
        }
        DailyProductDetailsRun run = new DailyProductDetailsRun();
        run.setMarketplace(marketplace);
        run.setRunDate(today);
        run.setTriggerSource(triggerSource);
        try {
            run = dailyProductDetailsRunRepository.save(run);
        } catch (DataIntegrityViolationException ex) {
            return new DailyProductDetailsTriggerResponse(
                    marketplace.getCode(),
                    today,
                    false,
                    "Bugun icin daha once kayit yapilmis."
            );
        }

        try {
            categoryRepository.findAll().stream()
                    .map(Category::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .forEach(name -> dailyProductDetailsService.recordDailyDetails(name, marketplace));
        } catch (RuntimeException ex) {
            dailyProductDetailsRunRepository.deleteById(run.getId());
            throw ex;
        }

        return new DailyProductDetailsTriggerResponse(
                marketplace.getCode(),
                today,
                true,
                "Kayit basariyla tamamlandi."
        );
    }

    private boolean isMarketplaceEnabled(Marketplace marketplace) {
        return switch (marketplace) {
            case MG -> migrosEnabled;
            case YS -> yemeksepetiEnabled;
        };
    }
}
