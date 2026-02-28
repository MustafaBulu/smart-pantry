package com.mustafabulu.smartpantry.common.core.util;

import com.mustafabulu.smartpantry.common.model.Product;
import com.mustafabulu.smartpantry.common.repository.ProductRepository;

public final class ProductUnitUpdater {

    private ProductUnitUpdater() {
    }

    public static void updateUnitIfMissing(
            Product product,
            String unit,
            Integer unitValue,
            ProductRepository productRepository
    ) {
        boolean updated = false;
        if (unit != null && !unit.isBlank() && product.getUnit() == null) {
            product.setUnit(unit);
            updated = true;
        }
        if (unitValue != null && product.getUnitValue() == null) {
            product.setUnitValue(unitValue);
            updated = true;
        }
        if (updated) {
            productRepository.save(product);
        }
    }
}
