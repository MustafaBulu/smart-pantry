package com.mustafabulu.smartpantry.service;

import com.mustafabulu.smartpantry.core.exception.SPException;
import com.mustafabulu.smartpantry.core.util.MarketplacePriceNormalizer;
import com.mustafabulu.smartpantry.core.response.ResponseMessages;
import com.mustafabulu.smartpantry.core.util.MarketplaceCodeResolver;
import com.mustafabulu.smartpantry.dto.response.CategoryPriceSummaryResponse;
import com.mustafabulu.smartpantry.dto.response.PriceHistoryResponse;
import com.mustafabulu.smartpantry.enums.Marketplace;
import com.mustafabulu.smartpantry.model.Category;
import com.mustafabulu.smartpantry.model.MigrosPriceHistoryCampaign;
import com.mustafabulu.smartpantry.model.PriceHistory;
import com.mustafabulu.smartpantry.model.Product;
import com.mustafabulu.smartpantry.repository.CategoryRepository;
import com.mustafabulu.smartpantry.repository.MigrosPriceHistoryCampaignRepository;
import com.mustafabulu.smartpantry.repository.PriceHistoryRepository;
import com.mustafabulu.smartpantry.repository.ProductRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class PriceHistoryService {
    private static final double PRICE_HISTORY_WEIGHT = 0.45;
    private static final double PERCENTILE_WEIGHT = 0.35;
    private static final double CROSS_MARKET_WEIGHT = 0.20;
    private static final double CROSS_MARKET_RATIO_MIN = 0.70;
    private static final double CROSS_MARKET_RATIO_MAX = 1.30;

    private final PriceHistoryRepository priceHistoryRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MigrosPriceHistoryCampaignRepository migrosPriceHistoryCampaignRepository;

    @Transactional(readOnly = true)
    public List<PriceHistoryResponse> getProductPrices(
            Long productId,
            String marketplaceCode,
            LocalDateTime startDate,
            LocalDateTime endDate,
            boolean useMoneyPrice,
            boolean useEffectivePrice
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
        return buildProductPriceResponses(
                product,
                histories,
                effectiveStart,
                endDate,
                useMoneyPrice,
                useEffectivePrice
        );
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

    private List<PriceHistoryResponse> buildProductPriceResponses(
            Product product,
            List<PriceHistory> histories,
            LocalDateTime startDate,
            LocalDateTime endDate,
            boolean useMoneyPrice,
            boolean useEffectivePrice
    ) {
        Map<String, MigrosPriceHistoryCampaign> migrosCampaignByKey =
                loadMigrosCampaignByKey(product.getId(), startDate, endDate);
        List<NormalizedPriceHistory> normalizedHistories = histories.stream()
                .map(history -> {
                    MigrosPriceHistoryCampaign campaign = null;
                    if (history.getMarketplace() == Marketplace.MG) {
                        String key = migrosCampaignKey(history);
                        if (key != null) {
                            campaign = migrosCampaignByKey.get(key);
                        }
                    }
                    return toNormalizedHistory(history, campaign, useMoneyPrice, useEffectivePrice);
                })
                .toList();

        Map<Marketplace, MarketStats> statsByMarketplace = buildMarketStats(normalizedHistories);
        Map<LocalDate, Map<Marketplace, BigDecimal>> pricesByDate = buildPricesByDate(normalizedHistories);
        Map<Marketplace, String> opportunityLevelsByMarketplace = buildOpportunityLevelsByMarketplace(normalizedHistories);

        return normalizedHistories.stream()
                .map(entry -> {
                    PriceHistory history = entry.history();
                    BigDecimal availabilityScore = calculateAvailabilityScore(
                            entry,
                            statsByMarketplace.get(history.getMarketplace()),
                            pricesByDate
                    );
                    return new PriceHistoryResponse(
                            history.getId(),
                            product.getId(),
                            product.getName(),
                            history.getMarketplace().getCode(),
                            entry.normalizedPrice(),
                            availabilityScore,
                            opportunityLevelsByMarketplace.getOrDefault(history.getMarketplace(), "Normal"),
                            history.getRecordedAt().toLocalDate()
                    );
                })
                .toList();
    }

    private NormalizedPriceHistory toNormalizedHistory(
            PriceHistory history,
            MigrosPriceHistoryCampaign campaign,
            boolean useMoneyPrice,
            boolean useEffectivePrice
    ) {
        BigDecimal selectedPrice = history.getPrice();
        if (history.getMarketplace() == Marketplace.MG && campaign != null) {
            if (useEffectivePrice && campaign.getEffectivePrice() != null) {
                selectedPrice = campaign.getEffectivePrice();
            } else if (useMoneyPrice && campaign.getMoneyPrice() != null) {
                selectedPrice = campaign.getMoneyPrice();
            }
        }
        return new NormalizedPriceHistory(
                history,
                MarketplacePriceNormalizer.normalizeForDisplay(history.getMarketplace(), selectedPrice)
        );
    }

    private Map<String, MigrosPriceHistoryCampaign> loadMigrosCampaignByKey(
            Long productId,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        LocalDate start = startDate == null ? LocalDate.now().minusYears(1) : startDate.toLocalDate();
        LocalDate end = endDate == null ? LocalDate.now() : endDate.toLocalDate();
        List<MigrosPriceHistoryCampaign> campaigns = migrosPriceHistoryCampaignRepository
                .findByProductIdAndDateBetween(productId, start, end);
        Map<String, MigrosPriceHistoryCampaign> result = new LinkedHashMap<>();
        for (MigrosPriceHistoryCampaign campaign : campaigns) {
            result.put(
                    campaign.getMarketplaceProductId() + ":" + campaign.getRecordedDate(),
                    campaign
            );
        }
        return result;
    }

    private String migrosCampaignKey(PriceHistory history) {
        if (history == null || history.getMarketplaceProduct() == null || history.getRecordedAt() == null) {
            return null;
        }
        return history.getMarketplaceProduct().getId() + ":" + history.getRecordedAt().toLocalDate();
    }

    private Map<Marketplace, MarketStats> buildMarketStats(List<NormalizedPriceHistory> entries) {
        Map<Marketplace, List<BigDecimal>> pricesByMarketplace = new EnumMap<>(Marketplace.class);
        for (NormalizedPriceHistory entry : entries) {
            pricesByMarketplace.computeIfAbsent(entry.history().getMarketplace(), ignored -> new ArrayList<>())
                    .add(entry.normalizedPrice());
        }

        Map<Marketplace, MarketStats> statsByMarketplace = new EnumMap<>(Marketplace.class);
        for (Map.Entry<Marketplace, List<BigDecimal>> entry : pricesByMarketplace.entrySet()) {
            List<BigDecimal> sortedPrices = new ArrayList<>(entry.getValue());
            sortedPrices.sort(Comparator.naturalOrder());
            BigDecimal minPrice = sortedPrices.getFirst();
            BigDecimal maxPrice = sortedPrices.getLast();
            statsByMarketplace.put(entry.getKey(), new MarketStats(minPrice, maxPrice, Collections.unmodifiableList(sortedPrices)));
        }
        return statsByMarketplace;
    }

    private Map<LocalDate, Map<Marketplace, BigDecimal>> buildPricesByDate(List<NormalizedPriceHistory> entries) {
        Map<LocalDate, Map<Marketplace, BigDecimal>> pricesByDate = new LinkedHashMap<>();
        for (NormalizedPriceHistory entry : entries) {
            LocalDate day = entry.history().getRecordedAt().toLocalDate();
            pricesByDate.computeIfAbsent(day, ignored -> new EnumMap<>(Marketplace.class))
                    .putIfAbsent(entry.history().getMarketplace(), entry.normalizedPrice());
        }
        return pricesByDate;
    }

    private BigDecimal calculateAvailabilityScore(
            NormalizedPriceHistory entry,
            MarketStats marketStats,
            Map<LocalDate, Map<Marketplace, BigDecimal>> pricesByDate
    ) {
        Double historyScore = calculateHistoryScore(entry.normalizedPrice(), marketStats);
        Double percentileScore = calculatePercentileScore(entry.normalizedPrice(), marketStats);
        Double crossMarketScore = calculateCrossMarketScore(entry, pricesByDate);

        double weightedSum = 0.0;
        double totalWeight = 0.0;
        if (historyScore != null) {
            weightedSum += historyScore * PRICE_HISTORY_WEIGHT;
            totalWeight += PRICE_HISTORY_WEIGHT;
        }
        if (percentileScore != null) {
            weightedSum += percentileScore * PERCENTILE_WEIGHT;
            totalWeight += PERCENTILE_WEIGHT;
        }
        if (crossMarketScore != null) {
            weightedSum += crossMarketScore * CROSS_MARKET_WEIGHT;
            totalWeight += CROSS_MARKET_WEIGHT;
        }
        double weightedScore = totalWeight == 0.0 ? 50.0 : weightedSum / totalWeight;

        return BigDecimal.valueOf(weightedScore).setScale(2, RoundingMode.HALF_UP);
    }

    private Double calculateHistoryScore(BigDecimal currentPrice, MarketStats stats) {
        if (stats == null || stats.maxPrice().compareTo(stats.minPrice()) == 0) {
            return null;
        }
        double min = stats.minPrice().doubleValue();
        double max = stats.maxPrice().doubleValue();
        double current = currentPrice.doubleValue();
        double score = ((max - current) / (max - min)) * 100.0;
        return clamp(score, 0.0, 100.0);
    }

    private Double calculatePercentileScore(BigDecimal currentPrice, MarketStats stats) {
        if (stats == null || stats.sortedPrices().size() <= 1) {
            return null;
        }
        List<BigDecimal> sortedPrices = stats.sortedPrices();
        int currentIndex = sortedPrices.indexOf(currentPrice);
        if (currentIndex < 0) {
            currentIndex = sortedPrices.size() - 1;
        }
        double denominator = sortedPrices.size() - 1.0;
        double score = (1.0 - (currentIndex / denominator)) * 100.0;
        return clamp(score, 0.0, 100.0);
    }

    private Double calculateCrossMarketScore(
            NormalizedPriceHistory entry,
            Map<LocalDate, Map<Marketplace, BigDecimal>> pricesByDate
    ) {
        Map<Marketplace, BigDecimal> pricesOnDate = pricesByDate.get(entry.history().getRecordedAt().toLocalDate());
        if (pricesOnDate == null) {
            return null;
        }
        Marketplace currentMarketplace = entry.history().getMarketplace();
        Marketplace otherMarketplace = currentMarketplace == Marketplace.YS ? Marketplace.MG : Marketplace.YS;
        BigDecimal otherPrice = pricesOnDate.get(otherMarketplace);
        if (otherPrice == null || entry.normalizedPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        double ratio = otherPrice.doubleValue() / entry.normalizedPrice().doubleValue();
        double boundedRatio = clamp(ratio, CROSS_MARKET_RATIO_MIN, CROSS_MARKET_RATIO_MAX);
        double score = ((boundedRatio - CROSS_MARKET_RATIO_MIN) / (CROSS_MARKET_RATIO_MAX - CROSS_MARKET_RATIO_MIN)) * 100.0;
        return clamp(score, 0.0, 100.0);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private Map<Marketplace, String> buildOpportunityLevelsByMarketplace(List<NormalizedPriceHistory> entries) {
        Map<Marketplace, List<BigDecimal>> pricesByMarketplace = new EnumMap<>(Marketplace.class);
        for (NormalizedPriceHistory entry : entries) {
            pricesByMarketplace.computeIfAbsent(entry.history().getMarketplace(), ignored -> new ArrayList<>())
                    .add(entry.normalizedPrice());
        }
        Map<Marketplace, String> result = new EnumMap<>(Marketplace.class);
        for (Map.Entry<Marketplace, List<BigDecimal>> entry : pricesByMarketplace.entrySet()) {
            result.put(entry.getKey(), resolveOpportunityLevel(entry.getValue()));
        }
        return result;
    }

    private String resolveOpportunityLevel(List<BigDecimal> prices) {
        if (prices == null || prices.size() < 6) {
            return "Normal";
        }
        List<BigDecimal> sorted = new ArrayList<>(prices);
        sorted.sort(Comparator.naturalOrder());
        BigDecimal median = resolveMedian(sorted);
        BigDecimal discountThreshold = median.multiply(BigDecimal.valueOf(0.93));
        long discountCount = prices.stream()
                .filter(price -> price.compareTo(discountThreshold) <= 0)
                .count();
        double discountRatio = discountCount / (double) prices.size();
        if (discountRatio >= 0.30) {
            return "Yuksek";
        }
        if (discountRatio >= 0.15) {
            return "Normal";
        }
        return "Orta";
    }

    private BigDecimal resolveMedian(List<BigDecimal> sortedValues) {
        int size = sortedValues.size();
        if (size == 0) {
            return BigDecimal.ZERO;
        }
        int middle = size / 2;
        if (size % 2 == 1) {
            return sortedValues.get(middle);
        }
        return sortedValues.get(middle - 1)
                .add(sortedValues.get(middle))
                .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    private record NormalizedPriceHistory(PriceHistory history, BigDecimal normalizedPrice) {
    }

    private record MarketStats(BigDecimal minPrice, BigDecimal maxPrice, List<BigDecimal> sortedPrices) {
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
            BigDecimal price = MarketplacePriceNormalizer.normalizeForDisplay(
                    history.getMarketplace(),
                    history.getPrice()
            );
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

