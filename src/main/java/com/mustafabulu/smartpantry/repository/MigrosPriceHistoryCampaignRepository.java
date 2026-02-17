package com.mustafabulu.smartpantry.repository;

import com.mustafabulu.smartpantry.model.MigrosPriceHistoryCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MigrosPriceHistoryCampaignRepository extends JpaRepository<MigrosPriceHistoryCampaign, Long> {

    Optional<MigrosPriceHistoryCampaign> findByMarketplaceProductIdAndRecordedDate(
            Long marketplaceProductId,
            LocalDate recordedDate
    );

    @Query("""
            select m
            from MigrosPriceHistoryCampaign m
            where m.productId = :productId
              and m.recordedDate >= :startDate
              and m.recordedDate <= :endDate
            """)
    List<MigrosPriceHistoryCampaign> findByProductIdAndDateBetween(
            @Param("productId") Long productId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Modifying
    @Query("""
            delete from MigrosPriceHistoryCampaign m
            where m.productId = :productId
            """)
    void deleteByProductId(@Param("productId") Long productId);

    @Modifying
    @Query("""
            delete from MigrosPriceHistoryCampaign m
            where m.marketplaceProductId = :marketplaceProductId
            """)
    void deleteByMarketplaceProductId(@Param("marketplaceProductId") Long marketplaceProductId);
}
