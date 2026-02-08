package com.mustafabulu.smartpantry.core.util;

import com.mustafabulu.smartpantry.model.Product;
import com.mustafabulu.smartpantry.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProductUnitUpdaterTest {

    @Test
    void updatesOnlyMissingFields() {
        Product product = new Product();
        product.setUnit("g");
        ProductRepository repository = Mockito.mock(ProductRepository.class);

        ProductUnitUpdater.updateUnitIfMissing(product, "ml", 250, repository);

        verify(repository).save(product);
    }

    @Test
    void doesNothingWhenNoUpdatesNeeded() {
        Product product = new Product();
        product.setUnit("g");
        product.setUnitValue(500);
        ProductRepository repository = Mockito.mock(ProductRepository.class);

        ProductUnitUpdater.updateUnitIfMissing(product, " ", null, repository);

        verify(repository, never()).save(product);
    }
}