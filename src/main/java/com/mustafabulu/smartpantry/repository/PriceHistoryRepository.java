package com.mustafabulu.smartpantry.repository;

import com.mustafabulu.smartpantry.model.PriceHistory;
import com.mustafabulu.smartpantry.model.Product;
import com.mustafabulu.smartpantry.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.enums.Marketplace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    @Query("""
            select distinct ph.product
            from PriceHistory ph
            where ph.marketplace = :marketplace
              and ph.product.category.name = :categoryName
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
            where ph.product.category.name = :categoryName
            """)
    List<Product> findDistinctProductsByCategory(
            @Param("categoryName") String categoryName
    );

    @Query("""
            select distinct ph.product
            from PriceHistory ph
            """)
    List<Product> findDistinctProducts();

    boolean existsByMarketplaceProductAndRecordedAtBetween(
            MarketplaceProduct marketplaceProduct,
            LocalDateTime start,
            LocalDateTime end
    );
}
