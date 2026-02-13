package com.mustafabulu.smartpantry.repository;

import com.mustafabulu.smartpantry.model.PriceHistory;
import com.mustafabulu.smartpantry.model.Product;
import com.mustafabulu.smartpantry.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.enums.Marketplace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    @Query("""
            select distinct ph.product
            from PriceHistory ph
            where ph.marketplace = :marketplace
              and lower(ph.product.category.name) = lower(:categoryName)
            """)
    List<Product> findDistinctProductsByMarketplaceAndCategory(
            @Param("marketplace") Marketplace marketplace,
            @Param("categoryName") String categoryName
    );

    @Query("""
            select distinct ph.product
            from PriceHistory ph
            where ph.marketplace = :marketplace
            """)
    List<Product> findDistinctProductsByMarketplace(
            @Param("marketplace") Marketplace marketplace
    );

    @Query("""
            select distinct ph.product
            from PriceHistory ph
            where lower(ph.product.category.name) = lower(:categoryName)
            """)
    List<Product> findDistinctProductsByCategory(
            @Param("categoryName") String categoryName
    );

    @Query("""
            select distinct ph.product
            from PriceHistory ph
            """)
    List<Product> findDistinctProducts();

    @Query("""
            select ph
            from PriceHistory ph
            where ph.product.id = :productId
              and ph.marketplace = coalesce(:marketplace, ph.marketplace)
              and ph.recordedAt >= coalesce(:startDate, ph.recordedAt)
              and ph.recordedAt <= coalesce(:endDate, ph.recordedAt)
            order by ph.recordedAt desc
            """)
    List<PriceHistory> findByProductIdAndFilters(
            @Param("productId") Long productId,
            @Param("marketplace") Marketplace marketplace,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("""
            select ph
            from PriceHistory ph
            where lower(ph.product.category.name) = lower(:categoryName)
              and ph.marketplace = coalesce(:marketplace, ph.marketplace)
              and ph.recordedAt >= coalesce(:startDate, ph.recordedAt)
              and ph.recordedAt <= coalesce(:endDate, ph.recordedAt)
            order by ph.product.id, ph.recordedAt desc
            """)
    List<PriceHistory> findByCategoryNameAndFilters(
            @Param("categoryName") String categoryName,
            @Param("marketplace") Marketplace marketplace,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    boolean existsByMarketplaceProductAndRecordedAtBetween(
            MarketplaceProduct marketplaceProduct,
            LocalDateTime start,
            LocalDateTime end
    );

    @Modifying
    @Query("""
            delete from PriceHistory ph
            where ph.product.id = :productId
            """)
    void deleteByProductId(@Param("productId") Long productId);

    @Modifying
    @Query("""
            delete from PriceHistory ph
            where ph.marketplaceProduct.id = :marketplaceProductId
            """)
    void deleteByMarketplaceProductId(@Param("marketplaceProductId") Long marketplaceProductId);

    @Query("""
            select ph
            from PriceHistory ph
            where ph.marketplaceProduct.id in :marketplaceProductIds
            order by ph.recordedAt desc
            """)
    List<PriceHistory> findByMarketplaceProductIds(
            @Param("marketplaceProductIds") List<Long> marketplaceProductIds
    );

    @Query("""
            select ph
            from PriceHistory ph
            where ph.marketplace = :marketplace
              and ph.product.id in :productIds
            order by ph.product.id, ph.recordedAt desc
            """)
    List<PriceHistory> findLatestByMarketplaceAndProductIds(
            @Param("marketplace") Marketplace marketplace,
            @Param("productIds") List<Long> productIds
    );
}
