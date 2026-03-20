package com.mustafabulu.smartpantry.common.repository;

import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.model.MarketplaceManualMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketplaceManualMatchRepository extends JpaRepository<MarketplaceManualMatch, Long> {
    List<MarketplaceManualMatch> findByCategoryId(Long categoryId);

    Optional<MarketplaceManualMatch> findByCategoryIdAndYsExternalIdAndMgExternalId(
            Long categoryId,
            String ysExternalId,
            String mgExternalId
    );

    void deleteByCategoryIdAndYsExternalId(Long categoryId, String ysExternalId);

    void deleteByCategoryIdAndMgExternalId(Long categoryId, String mgExternalId);

    void deleteByCategoryIdAndYsExternalIdAndMgExternalId(
            Long categoryId,
            String ysExternalId,
            String mgExternalId
    );

    void deleteByCategory(Category category);
}
