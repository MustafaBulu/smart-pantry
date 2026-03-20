package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.core.exception.SPException;
import com.mustafabulu.smartpantry.common.dto.response.CategoryPriceSummaryResponse;
import com.mustafabulu.smartpantry.common.dto.response.PriceHistoryResponse;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.model.Category;
import com.mustafabulu.smartpantry.common.model.MigrosPriceHistoryCampaign;
import com.mustafabulu.smartpantry.common.model.MarketplaceProduct;
import com.mustafabulu.smartpantry.common.model.PriceHistory;
import com.mustafabulu.smartpantry.common.model.Product;
import com.mustafabulu.smartpantry.common.repository.CategoryRepository;
import com.mustafabulu.smartpantry.common.repository.MigrosPriceHistoryCampaignRepository;
import com.mustafabulu.smartpantry.common.repository.PriceHistoryRepository;
import com.mustafabulu.smartpantry.common.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceHistoryServiceTest {

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MigrosPriceHistoryCampaignRepository migrosPriceHistoryCampaignRepository;

    @InjectMocks
    private PriceHistoryService priceHistoryService;

    @Test
    void getProductPricesDefaultsStartDate() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Chips");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(priceHistoryRepository.findByProductIdAndFilters(org.mockito.Mockito.eq(1L), org.mockito.Mockito.isNull(),
                org.mockito.Mockito.any(LocalDateTime.class), org.mockito.Mockito.isNull()))
                .thenReturn(List.of());

        when(migrosPriceHistoryCampaignRepository.findByProductIdAndDateBetween(
                org.mockito.Mockito.eq(1L),
                org.mockito.Mockito.any(LocalDate.class),
                org.mockito.Mockito.any(LocalDate.class)
        )).thenReturn(List.of());

        priceHistoryService.getProductPrices(1L, null, null, null, false, false);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(priceHistoryRepository).findByProductIdAndFilters(org.mockito.Mockito.eq(1L), org.mockito.Mockito.isNull(),
                captor.capture(), org.mockito.Mockito.isNull());
        LocalDateTime captured = captor.getValue();
        LocalDateTime now = LocalDateTime.now();
        assertTrue(captured.isBefore(now.plusSeconds(1)));
    }

    @Test
    void getProductPricesMapsResponses() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Chips");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        PriceHistory history = new PriceHistory();
        history.setId(5L);
        history.setMarketplace(Marketplace.MG);
        history.setPrice(BigDecimal.valueOf(12.5));
        history.setRecordedAt(LocalDateTime.of(2026, 2, 1, 10, 0));
        when(priceHistoryRepository.findByProductIdAndFilters(org.mockito.Mockito.eq(1L), org.mockito.Mockito.isNull(),
                org.mockito.Mockito.any(LocalDateTime.class), org.mockito.Mockito.isNull()))
                .thenReturn(List.of(history));

        when(migrosPriceHistoryCampaignRepository.findByProductIdAndDateBetween(
                org.mockito.Mockito.eq(1L),
                org.mockito.Mockito.any(LocalDate.class),
                org.mockito.Mockito.any(LocalDate.class)
        )).thenReturn(List.of());

        List<PriceHistoryResponse> responses = priceHistoryService.getProductPrices(
                1L,
                null,
                LocalDateTime.now().minusYears(1),
                null,
                false,
                false
        );

        assertEquals(1, responses.size());
        assertEquals(LocalDate.of(2026, 2, 1), responses.getFirst().recordedAt());
        assertNotNull(responses.getFirst().availabilityScore());
        assertNotNull(responses.getFirst().opportunityLevel());
    }

    @Test
    void getCategorySummaryDefaultsStartDateAndComputesStats() {
        Category category = new Category();
        category.setId(2L);
        category.setName("Snacks");
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.of(category));
        Product product = new Product();
        product.setId(3L);
        product.setName("Chips");
        PriceHistory first = new PriceHistory();
        first.setProduct(product);
        first.setPrice(BigDecimal.valueOf(10));
        first.setRecordedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        PriceHistory last = new PriceHistory();
        last.setProduct(product);
        last.setPrice(BigDecimal.valueOf(20));
        last.setRecordedAt(LocalDateTime.of(2026, 2, 1, 10, 0));
        when(priceHistoryRepository.findByCategoryNameAndFilters(org.mockito.Mockito.eq("Snacks"), org.mockito.Mockito.isNull(),
                org.mockito.Mockito.any(LocalDateTime.class), org.mockito.Mockito.isNull()))
                .thenReturn(List.of(last, first));

        List<CategoryPriceSummaryResponse> responses = priceHistoryService.getCategorySummary("Snacks", null, null, null);

        assertEquals(1, responses.size());
        CategoryPriceSummaryResponse response = responses.getFirst();
        assertEquals(BigDecimal.valueOf(10), response.minPrice());
        assertEquals(BigDecimal.valueOf(20), response.maxPrice());
        assertEquals(new BigDecimal("15.00"), response.avgPrice());
        assertEquals(LocalDate.of(2026, 2, 1), response.lastRecordedAt());
    }

    @Test
    void getCategorySummaryThrowsWhenMissingCategory() {
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.empty());

        assertThrows(SPException.class, () -> priceHistoryService.getCategorySummary("Snacks", null, null, null));
    }

    @Test
    void getProductPricesThrowsWhenMarketplaceInvalid() {
        Product product = new Product();
        product.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(SPException.class, () -> priceHistoryService.getProductPrices(1L, "XX", null, null, false, false));
    }

    @Test
    void getCategorySummaryThrowsWhenMarketplaceInvalid() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Snacks");
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(Optional.of(category));

        assertThrows(SPException.class, () -> priceHistoryService.getCategorySummary("Snacks", "XX", null, null));
    }

    @Test
    void getProductPricesUsesAvailableSignalsWithoutNeutralBias() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Cips");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        PriceHistory ysHistory = new PriceHistory();
        ysHistory.setId(11L);
        ysHistory.setProduct(product);
        ysHistory.setMarketplace(Marketplace.YS);
        ysHistory.setPrice(new BigDecimal("20.00"));
        ysHistory.setRecordedAt(LocalDateTime.of(2026, 2, 1, 10, 0));

        PriceHistory mgHistory = new PriceHistory();
        mgHistory.setId(12L);
        mgHistory.setProduct(product);
        mgHistory.setMarketplace(Marketplace.MG);
        mgHistory.setPrice(new BigDecimal("64.90"));
        mgHistory.setRecordedAt(LocalDateTime.of(2026, 2, 1, 10, 0));

        when(priceHistoryRepository.findByProductIdAndFilters(org.mockito.Mockito.eq(1L), org.mockito.Mockito.isNull(),
                org.mockito.Mockito.any(LocalDateTime.class), org.mockito.Mockito.isNull()))
                .thenReturn(List.of(ysHistory, mgHistory));
        when(migrosPriceHistoryCampaignRepository.findByProductIdAndDateBetween(
                org.mockito.Mockito.eq(1L),
                org.mockito.Mockito.any(LocalDate.class),
                org.mockito.Mockito.any(LocalDate.class)
        )).thenReturn(List.of());

        List<PriceHistoryResponse> responses = priceHistoryService.getProductPrices(
                1L,
                null,
                LocalDateTime.now().minusYears(1),
                null,
                false,
                false
        );

        Map<String, PriceHistoryResponse> byMarketplace = responses.stream()
                .collect(java.util.stream.Collectors.toMap(PriceHistoryResponse::marketplaceCode, response -> response));

        assertTrue(byMarketplace.containsKey("MG"));
        assertTrue(byMarketplace.containsKey("YS"));
        assertTrue(byMarketplace.get("MG").availabilityScore().doubleValue() < 40.0);
        assertNotEquals(byMarketplace.get("MG").availabilityScore(), byMarketplace.get("YS").availabilityScore());
    }

    @Test
    void getProductPricesUsesEffectiveCampaignPriceAndFlagsHighOpportunity() {
        Product product = new Product();
        product.setId(7L);
        product.setName("Kola");
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));

        List<PriceHistory> histories = new java.util.ArrayList<>();
        List<MigrosPriceHistoryCampaign> campaigns = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i += 1) {
            LocalDateTime recordedAt = LocalDateTime.of(2026, 2, i + 1, 10, 0);

            MarketplaceProduct mgMarketplaceProduct = new MarketplaceProduct();
            mgMarketplaceProduct.setId(100L + i);
            PriceHistory mgHistory = new PriceHistory();
            mgHistory.setId(1000L + i);
            mgHistory.setProduct(product);
            mgHistory.setMarketplaceProduct(mgMarketplaceProduct);
            mgHistory.setMarketplace(Marketplace.MG);
            mgHistory.setPrice(new BigDecimal("100.00"));
            mgHistory.setRecordedAt(recordedAt);
            histories.add(mgHistory);

            MigrosPriceHistoryCampaign campaign = new MigrosPriceHistoryCampaign();
            campaign.setMarketplaceProductId(100L + i);
            campaign.setProductId(7L);
            campaign.setRecordedDate(recordedAt.toLocalDate());
            campaign.setEffectivePrice(i < 3 ? new BigDecimal("50.00") : new BigDecimal("100.00"));
            campaigns.add(campaign);

            PriceHistory ysHistory = new PriceHistory();
            ysHistory.setId(2000L + i);
            ysHistory.setProduct(product);
            ysHistory.setMarketplace(Marketplace.YS);
            ysHistory.setPrice(new BigDecimal("95.00"));
            ysHistory.setRecordedAt(recordedAt);
            histories.add(ysHistory);
        }

        when(priceHistoryRepository.findByProductIdAndFilters(
                org.mockito.Mockito.eq(7L),
                org.mockito.Mockito.isNull(),
                org.mockito.Mockito.any(LocalDateTime.class),
                org.mockito.Mockito.isNull()
        )).thenReturn(histories);
        when(migrosPriceHistoryCampaignRepository.findByProductIdAndDateBetween(
                org.mockito.Mockito.eq(7L),
                org.mockito.Mockito.any(LocalDate.class),
                org.mockito.Mockito.any(LocalDate.class)
        )).thenReturn(campaigns);

        List<PriceHistoryResponse> responses = priceHistoryService.getProductPrices(
                7L,
                null,
                LocalDateTime.now().minusYears(1),
                null,
                false,
                true
        );

        List<PriceHistoryResponse> mgResponses = responses.stream()
                .filter(response -> "MG".equals(response.marketplaceCode()))
                .toList();
        assertEquals(6, mgResponses.size());
        assertTrue(mgResponses.stream().anyMatch(response -> new BigDecimal("50.00").compareTo(response.price()) == 0));
        assertTrue(mgResponses.stream().allMatch(response -> "Yuksek".equals(response.opportunityLevel())));
    }

    @Test
    void getCategorySummaryThrowsWhenCategoryNameBlank() {
        assertThrows(SPException.class, () -> priceHistoryService.getCategorySummary(" ", null, null, null));
    }
}
