package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.core.exception.SPException;
import com.mustafabulu.smartpantry.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.core.util.MarketplaceCodeResolver;
import com.mustafabulu.smartpantry.dto.response.CategoryPriceSummaryResponse;
import com.mustafabulu.smartpantry.dto.response.PriceHistoryResponse;
import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.model.PriceHistory;
import com.mustafabulu.smartpantry.model.Product;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.repository.PriceHistoryRepository;
import com.mustafabulu.smartpantry.repository.ProductRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class PriceHistoryService {

    private final PriceHistoryRepository priceHistoryRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<PriceHistoryResponse> getProductPrices(
            Long productId,
            String marketplaceCode,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new SPException(
                        HttpStatus.NOT_FOUND,
                        ResponseMessages.PRODUCT_NOT_FOUND,
                        ResponseMessages.PRODUCT_NOT_FOUND_CODE
                ));
        Marketplace marketplace = resolveMarketplace(marketplaceCode);
        LocalDateTime effectiveStart = startDate == null ? LocalDateTime.now().minusYears(1) : startDate;
        List<PriceHistory> histories = priceHistoryRepository.findByProductIdAndFilters(
                product.getId(),
                marketplace,
                effectiveStart,
                endDate
        );
        return histories.stream()
                .map(history -> new PriceHistoryResponse(
                        history.getId(),
                        product.getId(),
                        product.getName(),
                        history.getMarketplace().getCode(),
                        history.getPrice(),
                        history.getRecordedAt().toLocalDate()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryPriceSummaryResponse> getCategorySummary(
            String categoryName,
            String marketplaceCode,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        String trimmedCategory = categoryName == null ? "" : categoryName.trim();
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
        Marketplace marketplace = resolveMarketplace(marketplaceCode);
        LocalDateTime effectiveStart = startDate == null ? LocalDateTime.now().minusYears(1) : startDate;
        List<PriceHistory> histories = priceHistoryRepository.findByCategoryNameAndFilters(
                category.getName(),
                marketplace,
                effectiveStart,
                endDate
        );
        return buildCategorySummaryResponses(histories);
    }

    private List<CategoryPriceSummaryResponse> buildCategorySummaryResponses(List<PriceHistory> histories) {
        Map<Long, Summary> summaries = new LinkedHashMap<>();
        for (PriceHistory history : histories) {
            Product product = history.getProduct();
            Summary summary = summaries.computeIfAbsent(product.getId(), id -> new Summary(product.getName()));
            summary.accept(history);
        }
        List<CategoryPriceSummaryResponse> responses = new ArrayList<>();
        for (Map.Entry<Long, Summary> entry : summaries.entrySet()) {
            Summary summary = entry.getValue();
            responses.add(new CategoryPriceSummaryResponse(
                    entry.getKey(),
                    summary.productName,
                    summary.minPrice,
                    summary.maxPrice,
                    summary.average(),
                    summary.lastPrice,
                    summary.lastRecordedAt == null ? null : summary.lastRecordedAt.toLocalDate()
            ));
        }
        return responses;
    }

    private Marketplace resolveMarketplace(String marketplaceCode) {
        return MarketplaceCodeResolver.resolveNullable(marketplaceCode);
    }

    private static class Summary {
        private final String productName;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private BigDecimal totalPrice = BigDecimal.ZERO;
        private int count;
        private BigDecimal lastPrice;
        private LocalDateTime lastRecordedAt;

        Summary(String productName) {
            this.productName = productName;
        }

        void accept(PriceHistory history) {
            BigDecimal price = history.getPrice();
            if (minPrice == null || price.compareTo(minPrice) < 0) {
                minPrice = price;
            }
            if (maxPrice == null || price.compareTo(maxPrice) > 0) {
                maxPrice = price;
            }
            totalPrice = totalPrice.add(price);
            count++;
            if (lastRecordedAt == null || history.getRecordedAt().isAfter(lastRecordedAt)) {
                lastRecordedAt = history.getRecordedAt();
                lastPrice = price;
            }
        }

        BigDecimal average() {
            if (count == 0) {
                return BigDecimal.ZERO;
            }
            return totalPrice.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }
    }
}

