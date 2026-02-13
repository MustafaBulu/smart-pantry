package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.core.exception.SPException;
import com.mustafabulu.smartpantry.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.core.util.NameFormatter;
import com.mustafabulu.smartpantry.dto.response.ProductDetailResponse;
import com.mustafabulu.smartpantry.dto.request.ProductUpdateRequest;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.model.Product;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.repository.PriceHistoryRepository;
import com.mustafabulu.smartpantry.repository.ProductRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    public ProductDetailResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new SPException(
                        HttpStatus.NOT_FOUND,
                        ResponseMessages.PRODUCT_NOT_FOUND,
                        ResponseMessages.PRODUCT_NOT_FOUND_CODE
                ));
        return toDetailResponse(product);
    }

    @Transactional
    public ProductDetailResponse updateProduct(Long id, ProductUpdateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new SPException(
                        HttpStatus.NOT_FOUND,
                        ResponseMessages.PRODUCT_NOT_FOUND,
                        ResponseMessages.PRODUCT_NOT_FOUND_CODE
                ));
        if (request == null) {
            throw new SPException(
                    HttpStatus.BAD_REQUEST,
                    ResponseMessages.PRODUCT_UPDATE_EMPTY,
                    ResponseMessages.PRODUCT_UPDATE_EMPTY_CODE
            );
        }

        boolean updated = false;
        if (request.name() != null) {
            String trimmedName = request.name().trim();
            if (trimmedName.isBlank()) {
                throw new SPException(
                        HttpStatus.BAD_REQUEST,
                        ResponseMessages.PRODUCT_UPDATE_EMPTY,
                        ResponseMessages.PRODUCT_UPDATE_EMPTY_CODE
                );
            }
            product.setName(NameFormatter.capitalizeFirstLetter(trimmedName));
            updated = true;
        }
        if (request.brand() != null) {
            String trimmedBrand = request.brand().trim();
            product.setBrand(trimmedBrand.isBlank() ? null : trimmedBrand);
            updated = true;
        }
        if (request.unit() != null) {
            String trimmedUnit = request.unit().trim();
            product.setUnit(trimmedUnit.isBlank() ? null : trimmedUnit);
            updated = true;
        }
        if (request.unitValue() != null) {
            product.setUnitValue(request.unitValue());
            updated = true;
        }
        if (request.categoryName() != null) {
            String trimmedCategory = request.categoryName().trim();
            if (trimmedCategory.isBlank()) {
                throw new SPException(
                        HttpStatus.BAD_REQUEST,
                        ResponseMessages.CATEGORY_NAME_REQUIRED,
                        ResponseMessages.CATEGORY_NAME_REQUIRED_CODE
                );
            }
            Category category = categoryRepository.findByNameIgnoreCase(trimmedCategory)
                    .orElseThrow(() -> new SPException(
                            HttpStatus.NOT_FOUND,
                            ResponseMessages.CATEGORY_NOT_FOUND,
                            ResponseMessages.CATEGORY_NOT_FOUND_CODE
                    ));
            product.setCategory(category);
            updated = true;
        }

        if (!updated) {
            throw new SPException(
                    HttpStatus.BAD_REQUEST,
                    ResponseMessages.PRODUCT_UPDATE_EMPTY,
                    ResponseMessages.PRODUCT_UPDATE_EMPTY_CODE
            );
        }

        Product saved = productRepository.save(product);
        return toDetailResponse(saved);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new SPException(
                        HttpStatus.NOT_FOUND,
                        ResponseMessages.PRODUCT_NOT_FOUND,
                        ResponseMessages.PRODUCT_NOT_FOUND_CODE
                ));
        priceHistoryRepository.deleteByProductId(product.getId());
        productRepository.delete(product);
    }

    private ProductDetailResponse toDetailResponse(Product product) {
        Category category = product.getCategory();
        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getUnit(),
                product.getUnitValue(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                product.getCreatedAt()
        );
    }
}
