package com.mustafabulu.smartpantry.common.repository;

import com.mustafabulu.smartpantry.common.model.ImageSignatureCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImageSignatureCacheRepository extends JpaRepository<ImageSignatureCache, Long> {
    Optional<ImageSignatureCache> findByNormalizedUrl(String normalizedUrl);
}
