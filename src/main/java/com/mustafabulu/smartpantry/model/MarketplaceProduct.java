package com.mustafabulu.smartpantry.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mustafabulu.smartpantry.enums.Marketplace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "marketplace_products")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@Setter
@RequiredArgsConstructor
public class MarketplaceProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "marketplace", nullable = false, length = 10)
    private Marketplace marketplace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String externalId;

    @Column
    private String productUrl;

    @Column
    private String brandName;

    @Column(length = 1024)
    private String imageUrl;

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
}
