package com.mustafabulu.smartpantry.model;

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
import java.time.LocalDate;

@Entity
@Table(name = "migros_price_history_campaigns")
@Getter
@Setter
@RequiredArgsConstructor
public class MigrosPriceHistoryCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long marketplaceProductId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private LocalDate recordedDate;

    @Column
    private Integer campaignBuyQuantity;

    @Column
    private Integer campaignPayQuantity;

    @Column(precision = 19, scale = 2)
    private BigDecimal moneyPrice;

    @Column(precision = 19, scale = 2)
    private BigDecimal effectivePrice;
}
