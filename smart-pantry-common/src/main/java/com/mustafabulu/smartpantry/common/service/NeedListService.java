package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.dto.request.NeedListItemRequest;
import com.mustafabulu.smartpantry.common.dto.response.NeedListItemResponse;
import com.mustafabulu.smartpantry.common.model.NeedListItem;
import com.mustafabulu.smartpantry.common.repository.NeedListItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NeedListService {

    private final NeedListItemRepository needListItemRepository;

    @Transactional(readOnly = true)
    public List<NeedListItemResponse> listItems() {
        return needListItemRepository.findAllByOrderByIdDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public synchronized List<NeedListItemResponse> replaceAll(List<NeedListItemRequest> request) {
        needListItemRepository.deleteAllInBatch();
        if (request == null || request.isEmpty()) {
            return List.of();
        }
        Map<String, NeedListItem> uniqueEntries = new LinkedHashMap<>();
        for (NeedListItemRequest item : request) {
            if (item == null || item.key() == null || item.key().isBlank()) {
                continue;
            }
            String normalizedKey = item.key().trim();
            NeedListItem entity = toEntity(item);
            entity.setItemKey(normalizedKey);
            uniqueEntries.put(normalizedKey, entity);
        }
        List<NeedListItem> entries = new ArrayList<>(uniqueEntries.values());
        if (entries.isEmpty()) {
            return List.of();
        }
        return needListItemRepository.saveAll(entries).stream()
                .map(this::toResponse)
                .toList();
    }

    private NeedListItem toEntity(NeedListItemRequest request) {
        NeedListItem item = new NeedListItem();
        item.setItemKey(request.key());
        item.setItemType(defaultString(request.type(), "CATEGORY"));
        item.setCategoryId(request.categoryId() == null ? 0L : request.categoryId());
        item.setCategoryName(defaultString(request.categoryName(), ""));
        item.setExternalId(request.externalId());
        item.setMarketplaceCode(request.marketplaceCode());
        item.setName(defaultString(request.name(), ""));
        item.setImageUrl(request.imageUrl());
        item.setPrice(request.price());
        item.setMoneyPrice(request.moneyPrice());
        item.setBasketDiscountThreshold(request.basketDiscountThreshold());
        item.setBasketDiscountPrice(request.basketDiscountPrice());
        item.setCampaignBuyQuantity(request.campaignBuyQuantity());
        item.setCampaignPayQuantity(request.campaignPayQuantity());
        item.setEffectivePrice(request.effectivePrice());
        item.setUrgency(defaultString(request.urgency(), "URGENT"));
        item.setAvailabilityScore(request.availabilityScore());
        item.setHistoryDayCount(request.historyDayCount());
        item.setAvailabilityStatus(defaultString(request.availabilityStatus(), "Normal"));
        item.setOpportunityLevel(request.opportunityLevel());
        return item;
    }

    private NeedListItemResponse toResponse(NeedListItem item) {
        return new NeedListItemResponse(
                item.getItemKey(),
                item.getItemType(),
                item.getCategoryId(),
                item.getCategoryName(),
                item.getExternalId(),
                item.getMarketplaceCode(),
                item.getName(),
                item.getImageUrl(),
                item.getPrice(),
                item.getMoneyPrice(),
                item.getBasketDiscountThreshold(),
                item.getBasketDiscountPrice(),
                item.getCampaignBuyQuantity(),
                item.getCampaignPayQuantity(),
                item.getEffectivePrice(),
                item.getUrgency(),
                item.getAvailabilityScore(),
                item.getHistoryDayCount(),
                item.getAvailabilityStatus(),
                item.getOpportunityLevel()
        );
    }

    private String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
