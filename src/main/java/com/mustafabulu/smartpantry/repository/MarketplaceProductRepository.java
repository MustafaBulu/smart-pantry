package com.mustafabulu.smartpantry.repository;

import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketplaceProductRepository extends JpaRepository<MarketplaceProduct, Long> {
    List<MarketplaceProduct> findByMarketplaceAndCategory(Marketplace marketplace, Category category);

    List<MarketplaceProduct> findByMarketplaceAndExternalId(Marketplace marketplace, String externalId);

    boolean existsByCategory(Category category);

    Optional<MarketplaceProduct> findByMarketplaceAndCategoryAndExternalId(
            Marketplace marketplace,
            Category category,
            String externalId
    );
}
