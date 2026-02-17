package com.mustafabulu.smartpantry.migros.service;

import com.mustafabulu.smartpantry.config.MarketplaceUrlProperties;
import com.mustafabulu.smartpantry.core.exception.SPException;
import com.mustafabulu.smartpantry.migros.model.MigrosProductDetails;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.model.MigrosPriceHistoryCampaign;
import com.mustafabulu.smartpantry.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.model.PriceHistory;
import com.mustafabulu.smartpantry.model.Product;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.repository.MigrosPriceHistoryCampaignRepository;
import com.mustafabulu.smartpantry.repository.MarketplaceProductRepository;
import com.mustafabulu.smartpantry.repository.PriceHistoryRepository;
import com.mustafabulu.smartpantry.repository.ProductRepository;
import com.mustafabulu.smartpantry.service.PlatformProductDetailsService;
import com.mustafabulu.smartpantry.core.util.ProductUrlResolver;
import com.mustafabulu.smartpantry.core.util.ProductUnitUpdater;
import com.mustafabulu.smartpantry.core.util.NameFormatter;
import lombok.AllArgsConstructor;
import com.mustafabulu.smartpantry.core.log.LogMessages;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@AllArgsConstructor
@Slf4j
public class MigrosProductDetailsService implements PlatformProductDetailsService {

    private static final Marketplace MARKETPLACE = Marketplace.MG;

    private final MigrosScraperService scraperService;
    private final CategoryRepository categoryRepository;
    private final MarketplaceProductRepository marketplaceProductRepository;
    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MigrosPriceHistoryCampaignRepository migrosPriceHistoryCampaignRepository;
    private final MarketplaceUrlProperties marketplaceUrlProperties;

    @Override
    public void recordDailyDetails(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return;
        }
        String trimmedCategory = categoryName.trim();
        String normalizedCategory = normalizeCategory(trimmedCategory);
        if (normalizedCategory.isBlank()) {
            return;
        }

        Category category = categoryRepository.findByNameIgnoreCase(trimmedCategory)
                .orElse(null);
        if (category == null) {
            return;
        }

        List<MarketplaceProduct> marketplaceProducts = marketplaceProductRepository
                .findByMarketplaceAndCategory(MARKETPLACE, category);
        if (marketplaceProducts.isEmpty()) {
            return;
        }

        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        for (MarketplaceProduct marketplaceProduct : marketplaceProducts) {
            recordDetailsForProduct(category, marketplaceProduct, dayStart, dayEnd);
        }
    }

    public boolean recordDetailsForProduct(Category category, MarketplaceProduct marketplaceProduct) {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        return recordDetailsForProduct(category, marketplaceProduct, dayStart, dayEnd);
    }

    private boolean recordDetailsForProduct(
            Category category,
            MarketplaceProduct marketplaceProduct,
            LocalDateTime dayStart,
            LocalDateTime dayEnd
    ) {
        if (isAlreadyRecorded(marketplaceProduct, dayStart, dayEnd)) {
            return true;
        }

        MigrosProductDetails details = fetchDetails(marketplaceProduct);
        if (details == null) {
            return false;
        }
        updateMarketplacePricingMetadata(marketplaceProduct, details);

        Product product = findOrCreateProduct(category, marketplaceProduct, details);
        updateProductIfMissing(product, details);
        PriceHistory history = saveHistory(product, marketplaceProduct, details);
        saveCampaignHistory(product, marketplaceProduct, details, history);
        logHistory(category, product, marketplaceProduct, history);
        return true;
    }

    private boolean isAlreadyRecorded(
            MarketplaceProduct marketplaceProduct,
            LocalDateTime dayStart,
            LocalDateTime dayEnd
    ) {
        return priceHistoryRepository.existsByMarketplaceProductAndRecordedAtBetween(
                marketplaceProduct,
                dayStart,
                dayEnd
        );
    }

    private MigrosProductDetails fetchDetails(MarketplaceProduct marketplaceProduct) {
        String url = resolveProductUrl(marketplaceProduct);
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return scraperService.fetchProductDetails(url);
        } catch (SPException ex) {
            log.warn(LogMessages.MIGROS_FETCH_FAILED,
                    marketplaceProduct.getExternalId(),
                    ex.getStatusCode()
            );
            return null;
        }
    }

    private Product findOrCreateProduct(
            Category category,
            MarketplaceProduct marketplaceProduct,
            MigrosProductDetails details
    ) {
        String productName = resolveProductName(details, marketplaceProduct);
        return productRepository.findByNameIgnoreCaseAndCategory(productName, category)
                .orElseGet(() -> {
                    Product created = new Product();
                    created.setCategory(category);
                    created.setName(productName);
                    created.setBrand(details.brand());
                    applyUnitDetails(created, details);
                    return productRepository.save(created);
                });
    }

    private String resolveProductName(MigrosProductDetails details, MarketplaceProduct marketplaceProduct) {
        String detailsName = details.name();
        String resolvedName = (detailsName != null && !detailsName.isBlank())
                ? detailsName
                : "Migros Urun " + marketplaceProduct.getExternalId();
        return NameFormatter.capitalizeFirstLetter(resolvedName);
    }

    private void updateProductIfMissing(Product product, MigrosProductDetails details) {
        boolean updated = false;
        String normalizedName = NameFormatter.capitalizeFirstLetter(product.getName());
        if (normalizedName != null && !normalizedName.equals(product.getName())) {
            product.setName(normalizedName);
            updated = true;
        }
        if (details.brand() != null && !details.brand().isBlank() && product.getBrand() == null) {
            product.setBrand(details.brand());
            updated = true;
        }
        if (updated) {
            productRepository.save(product);
        }
        ProductUnitUpdater.updateUnitIfMissing(
                product,
                details.unit(),
                details.unitValue(),
                productRepository
        );
    }

    private void applyUnitDetails(Product product, MigrosProductDetails details) {
        if (details.unit() != null && !details.unit().isBlank()) {
            product.setUnit(details.unit());
        }
        if (details.unitValue() != null) {
            product.setUnitValue(details.unitValue());
        }
    }

    private PriceHistory saveHistory(
            Product product,
            MarketplaceProduct marketplaceProduct,
            MigrosProductDetails details
    ) {
        PriceHistory history = new PriceHistory();
        history.setMarketplace(MARKETPLACE);
        history.setProduct(product);
        history.setMarketplaceProduct(marketplaceProduct);
        history.setPrice(BigDecimal.valueOf(details.currentPrice()));
        priceHistoryRepository.save(history);
        return history;
    }

    private void updateMarketplacePricingMetadata(
            MarketplaceProduct marketplaceProduct,
            MigrosProductDetails details
    ) {
        boolean updated = false;
        if (details.basketDiscountThreshold() != null
                && !details.basketDiscountThreshold().equals(marketplaceProduct.getBasketDiscountThreshold())) {
            marketplaceProduct.setBasketDiscountThreshold(details.basketDiscountThreshold());
            updated = true;
        }
        if (details.basketDiscountPrice() != null
                && !details.basketDiscountPrice().equals(marketplaceProduct.getBasketDiscountPrice())) {
            marketplaceProduct.setBasketDiscountPrice(details.basketDiscountPrice());
            updated = true;
        }
        if (details.campaignBuyQuantity() != null
                && !details.campaignBuyQuantity().equals(marketplaceProduct.getCampaignBuyQuantity())) {
            marketplaceProduct.setCampaignBuyQuantity(details.campaignBuyQuantity());
            updated = true;
        }
        if (details.campaignPayQuantity() != null
                && !details.campaignPayQuantity().equals(marketplaceProduct.getCampaignPayQuantity())) {
            marketplaceProduct.setCampaignPayQuantity(details.campaignPayQuantity());
            updated = true;
        }
        if (details.effectivePrice() != null
                && !details.effectivePrice().equals(marketplaceProduct.getEffectivePrice())) {
            marketplaceProduct.setEffectivePrice(details.effectivePrice());
            updated = true;
        }
        if (updated) {
            marketplaceProductRepository.save(marketplaceProduct);
        }
    }

    private void saveCampaignHistory(
            Product product,
            MarketplaceProduct marketplaceProduct,
            MigrosProductDetails details,
            PriceHistory history
    ) {
        if (history == null || history.getRecordedAt() == null) {
            return;
        }
        java.time.LocalDate recordedDate = history.getRecordedAt().toLocalDate();
        MigrosPriceHistoryCampaign campaign = migrosPriceHistoryCampaignRepository
                .findByMarketplaceProductIdAndRecordedDate(marketplaceProduct.getId(), recordedDate)
                .orElseGet(MigrosPriceHistoryCampaign::new);
        campaign.setMarketplaceProductId(marketplaceProduct.getId());
        campaign.setProductId(product.getId());
        campaign.setRecordedDate(recordedDate);
        campaign.setCampaignBuyQuantity(details.campaignBuyQuantity());
        campaign.setCampaignPayQuantity(details.campaignPayQuantity());
        campaign.setMoneyPrice(details.moneyPrice() != null ? details.moneyPrice() : marketplaceProduct.getMoneyPrice());
        campaign.setEffectivePrice(
                details.effectivePrice() != null ? details.effectivePrice() : marketplaceProduct.getEffectivePrice()
        );
        migrosPriceHistoryCampaignRepository.save(campaign);
    }

    private void logHistory(
            Category category,
            Product product,
            MarketplaceProduct marketplaceProduct,
            PriceHistory history
    ) {
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
    }

    private String resolveProductUrl(MarketplaceProduct marketplaceProduct) {
        return ProductUrlResolver.resolveProductUrl(
                marketplaceProduct,
                marketplaceUrlProperties.getMigrosPrefix(),
                marketplaceUrlProperties.getMigrosSuffix()
        );
    }

    private String normalizeCategory(String categoryName) {
        return categoryName == null ? "" : categoryName.trim().toLowerCase(Locale.ROOT);
    }
}
