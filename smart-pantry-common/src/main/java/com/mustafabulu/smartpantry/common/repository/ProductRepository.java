package com.mustafabulu.smartpantry.common.repository;

import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByNameIgnoreCaseAndCategory(String name, Category category);
    List<Product> findByCategory(Category category);

}
