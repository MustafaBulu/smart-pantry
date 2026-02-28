package com.mustafabulu.smartpantry.common.repository;

import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.common.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketplaceProductRepository extends JpaRepository<MarketplaceProduct, Long> {
    List<MarketplaceProduct> findByMarketplace(Marketplace marketplace);

    List<MarketplaceProduct> findByMarketplaceAndCategory(Marketplace marketplace, Category category);

    List<MarketplaceProduct> findByCategory(Category category);

    List<MarketplaceProduct> findByMarketplaceAndExternalId(Marketplace marketplace, String externalId);

    Optional<MarketplaceProduct> findByMarketplaceAndCategoryAndExternalId(
            Marketplace marketplace,
            Category category,
            String externalId
    );
}
