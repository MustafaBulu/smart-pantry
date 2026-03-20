package com.mustafabulu.smartpantry.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@SuppressWarnings("JpaDataSourceORMInspection")
@Table(
        name = "marketplace_manual_matches",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_marketplace_manual_matches_category_ys_mg",
                        columnNames = {"category_id", "ys_external_id", "mg_external_id"}
                ),
                @UniqueConstraint(
                        name = "uk_marketplace_manual_matches_category_ys",
                        columnNames = {"category_id", "ys_external_id"}
                ),
                @UniqueConstraint(
                        name = "uk_marketplace_manual_matches_category_mg",
                        columnNames = {"category_id", "mg_external_id"}
                )
        }
)
@Getter
@Setter
@RequiredArgsConstructor
public class MarketplaceManualMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "ys_external_id", nullable = false)
    private String ysExternalId;

    @Column(name = "mg_external_id", nullable = false)
    private String mgExternalId;
}
