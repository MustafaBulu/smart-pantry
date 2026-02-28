package com.mustafabulu.smartpantry.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "need_list_items")
@SuppressWarnings("JpaDataSourceORMInspection")
@Getter
@Setter
@RequiredArgsConstructor
public class NeedListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String itemKey;

    @Column(nullable = false, length = 20)
    private String itemType;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private String categoryName;

    @Column
    private String externalId;

    @Column(length = 10)
    private String marketplaceCode;

    @Column(nullable = false)
    private String name;

    @Column(length = 1024)
    private String imageUrl;

    @Column(precision = 19, scale = 2)
    private BigDecimal price;

    @Column(precision = 19, scale = 2)
    private BigDecimal moneyPrice;

    @Column(precision = 19, scale = 2)
    private BigDecimal basketDiscountThreshold;

    @Column(precision = 19, scale = 2)
    private BigDecimal basketDiscountPrice;

    @Column
    private Integer campaignBuyQuantity;

    @Column
    private Integer campaignPayQuantity;

    @Column(precision = 19, scale = 2)
    private BigDecimal effectivePrice;

    @Column(nullable = false, length = 20)
    private String urgency;

    @Column(precision = 19, scale = 2)
    private BigDecimal availabilityScore;

    @Column
    private Integer historyDayCount;

    @Column(nullable = false, length = 20)
    private String availabilityStatus;

    @Column(length = 20)
    private String opportunityLevel;
}
