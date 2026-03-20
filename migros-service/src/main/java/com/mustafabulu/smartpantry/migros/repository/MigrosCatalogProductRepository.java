package com.mustafabulu.smartpantry.migros.repository;

import com.mustafabulu.smartpantry.migros.model.MigrosCatalogProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MigrosCatalogProductRepository extends JpaRepository<MigrosCatalogProduct, Long> {

    List<MigrosCatalogProduct> findByExternalIdIn(Collection<String> externalIds);

    @Query("""
            select p
            from MigrosCatalogProduct p
            where lower(p.productName) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(p.brandName, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(p.categoryName, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(p.prettyName, '')) like lower(concat('%', :keyword, '%'))
            order by p.id asc
            """)
    List<MigrosCatalogProduct> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
