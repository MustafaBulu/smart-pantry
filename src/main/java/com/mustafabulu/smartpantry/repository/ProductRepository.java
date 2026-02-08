package com.mustafabulu.smartpantry.repository;

import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByNameAndCategory(String name, Category category);

    boolean existsByCategory(Category category);
}
