package com.mustafabulu.smartpantry.migros.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "migros_products",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_migros_products_external_id",
                columnNames = {"external_id"}
        )
)
@Getter
@Setter
public class MigrosCatalogProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "product_name", nullable = false, length = 1024)
    private String productName;

    @Column(name = "brand_name", length = 255)
    private String brandName;

    @Column(name = "pretty_name", length = 1024)
    private String prettyName;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "category_name", length = 255)
    private String categoryName;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(name = "status", length = 64)
    private String status;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "regular_price", precision = 19, scale = 2)
    private BigDecimal regularPrice;

    @Column(name = "shown_price", precision = 19, scale = 2)
    private BigDecimal shownPrice;

    @Column(name = "unit_price", length = 128)
    private String unitPrice;

    @Column(name = "discount_rate")
    private Integer discountRate;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}
