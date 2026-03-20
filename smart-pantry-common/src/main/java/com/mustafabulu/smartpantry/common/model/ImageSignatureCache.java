package com.mustafabulu.smartpantry.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@SuppressWarnings("JpaDataSourceORMInspection")
@Table(
        name = "image_signature_cache",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_image_signature_cache_normalized_url",
                columnNames = {"normalized_url"}
        )
)
@Getter
@Setter
@RequiredArgsConstructor
public class ImageSignatureCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "normalized_url", nullable = false, length = 1024)
    private String normalizedUrl;

    @Column(name = "unavailable", nullable = false)
    private boolean unavailable;

    @Column(name = "full_a_hash")
    private Long fullAHash;

    @Column(name = "full_d_hash")
    private Long fullDHash;

    @Column(name = "center_a_hash")
    private Long centerAHash;

    @Column(name = "center_d_hash")
    private Long centerDHash;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
