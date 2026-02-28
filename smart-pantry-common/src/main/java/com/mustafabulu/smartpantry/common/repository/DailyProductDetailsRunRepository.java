package com.mustafabulu.smartpantry.common.repository;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.model.DailyProductDetailsRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyProductDetailsRunRepository extends JpaRepository<DailyProductDetailsRun, Long> {

    Optional<DailyProductDetailsRun> findByMarketplaceAndRunDate(Marketplace marketplace, LocalDate runDate);
}
