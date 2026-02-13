package com.mustafabulu.smartpantry.yemeksepeti.service;

import com.mustafabulu.smartpantry.config.MarketplaceUrlProperties;
import com.mustafabulu.smartpantry.yemeksepeti.model.YemeksepetiProductDetails;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.model.PriceHistory;
import com.mustafabulu.smartpantry.model.Product;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.repository.PriceHistoryRepository;
import com.mustafabulu.smartpantry.repository.ProductRepository;
import com.mustafabulu.smartpantry.service.PlatformProductDetailsService;
import com.mustafabulu.smartpantry.core.log.LogMessages;
import com.mustafabulu.smartpantry.core.util.ProductUrlResolver;
import com.mustafabulu.smartpantry.core.util.ProductUnitUpdater;
import com.mustafabulu.smartpantry.core.util.NameFormatter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

@Service
@AllArgsConstructor
@Slf4j
public class YemeksepetiProductDetailsService implements PlatformProductDetailsService {

    private static final Marketplace MARKETPLACE = Marketplace.YS;

    private final YemeksepetiScraperService scraperService;
    private final CategoryRepository categoryRepository;
    private final MarketplaceProductRepository marketplaceProductRepository;
    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MarketplaceUrlProperties marketplaceUrlProperties;

    @Override
    public void recordDailyDetails(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return;
        }
        String trimmedCategory = categoryName.trim();
        Category category = categoryRepository.findByNameIgnoreCase(trimmedCategory)
                .orElseGet(() -> {
                    Category created = new Category();
                    created.setName(trimmedCategory);
                    return categoryRepository.save(created);
                });

        List<MarketplaceProduct> marketplaceProducts = marketplaceProductRepository
                .findByMarketplaceAndCategory(MARKETPLACE, category);
        if (marketplaceProducts.isEmpty()) {
            return;
        }

        for (MarketplaceProduct marketplaceProduct : marketplaceProducts) {
            recordDetailsForProduct(category, marketplaceProduct);
        }
    }

    public boolean recordDetailsForProduct(Category category, MarketplaceProduct marketplaceProduct) {
        String url = resolveProductUrl(marketplaceProduct);
        if (url == null || url.isBlank()) {
            return false;
        }
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        boolean alreadyRecorded = priceHistoryRepository
                .existsByMarketplaceProductAndRecordedAtBetween(marketplaceProduct, dayStart, dayEnd);
        if (alreadyRecorded) {
            return true;
        }
        YemeksepetiProductDetails details = scraperService.fetchProductDetails(url);
        if (details == null) {
            return false;
        }

        String detailsName = details.name();
        String rawName = (detailsName != null && !detailsName.isBlank())
                ? detailsName
                : "Yemeksepeti Urun " + marketplaceProduct.getExternalId();
        String productName = NameFormatter.capitalizeFirstLetter(rawName);
        Product product = productRepository.findByNameIgnoreCaseAndCategory(productName, category)
                .orElseGet(() -> {
                    Product created = new Product();
                    created.setCategory(category);
                    created.setName(productName);
                    if (details.unit() != null && !details.unit().isBlank()) {
                        created.setUnit(details.unit());
                    }
                    if (details.unitValue() != null) {
                        created.setUnitValue(details.unitValue());
                    }
                    return productRepository.save(created);
                });
        String normalizedName = NameFormatter.capitalizeFirstLetter(product.getName());
        if (normalizedName != null && !normalizedName.equals(product.getName())) {
            product.setName(normalizedName);
            productRepository.save(product);
        }
        ProductUnitUpdater.updateUnitIfMissing(
                product,
                details.unit(),
                details.unitValue(),
                productRepository
        );

        PriceHistory history = new PriceHistory();
        history.setMarketplace(MARKETPLACE);
        history.setProduct(product);
        history.setMarketplaceProduct(marketplaceProduct);
        history.setPrice(BigDecimal.valueOf(details.getAdjustedPrice()));
        priceHistoryRepository.save(history);

        log.info(
                LogMessages.DAILY_PRODUCT_LOGGED,
                category.getName(),
                MARKETPLACE.getCode(),
                MARKETPLACE.getDisplayName(),
                LocalDate.now(),
                product.getId(),
                product.getName(),
                marketplaceProduct.getExternalId(),
                history.getPrice()
        );
        return true;
    }

    private String resolveProductUrl(MarketplaceProduct marketplaceProduct) {
        return ProductUrlResolver.resolveProductUrl(
                marketplaceProduct,
                marketplaceUrlProperties.getYemeksepetiBase(),
                ""
        );
    }
}
