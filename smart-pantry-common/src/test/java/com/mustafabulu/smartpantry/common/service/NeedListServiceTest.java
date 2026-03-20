package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.dto.request.NeedListItemRequest;
import com.mustafabulu.smartpantry.common.dto.response.NeedListItemResponse;
import com.mustafabulu.smartpantry.common.model.NeedListItem;
import com.mustafabulu.smartpantry.common.repository.NeedListItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NeedListServiceTest {

    @Mock
    private NeedListItemRepository needListItemRepository;

    @InjectMocks
    private NeedListService service;

    @Test
    void listItemsMapsRepositoryResults() {
        NeedListItem item = new NeedListItem();
        item.setItemKey("milk");
        item.setItemType("CATEGORY");
        item.setCategoryId(10L);
        item.setCategoryName("Dairy");
        item.setName("Milk");
        item.setUrgency("URGENT");
        item.setAvailabilityStatus("Normal");

        when(needListItemRepository.findAllByOrderByIdDesc()).thenReturn(List.of(item));

        List<NeedListItemResponse> response = service.listItems();

        assertEquals(1, response.size());
        assertEquals("milk", response.getFirst().key());
        assertEquals("Milk", response.getFirst().name());
    }

    @Test
    void replaceAllReturnsEmptyWhenRequestIsNull() {
        List<NeedListItemResponse> response = service.replaceAll(null);

        assertEquals(List.of(), response);
        verify(needListItemRepository).deleteAllInBatch();
    }

    @Test
    void replaceAllFiltersInvalidRowsAndKeepsLastDuplicate() {
        NeedListItemRequest invalid = new NeedListItemRequest(
                " ", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        NeedListItemRequest first = new NeedListItemRequest(
                " milk ", null, null, null, null, null, "Old Milk", null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        NeedListItemRequest last = new NeedListItemRequest(
                "milk", "PRODUCT", 4L, "Dairy", "ext-1", "YS", "New Milk", "img",
                new BigDecimal("10.00"), null, null, null, null, null, null, "LOW", null, null, "Normal", "GOOD"
        );

        when(needListItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<NeedListItemResponse> response = service.replaceAll(List.of(invalid, first, last));

        assertEquals(1, response.size());
        assertEquals("milk", response.getFirst().key());
        assertEquals("PRODUCT", response.getFirst().type());
        assertEquals("New Milk", response.getFirst().name());
    }

    @Test
    void replaceAllAppliesDefaultValues() {
        NeedListItemRequest request = new NeedListItemRequest(
                "bread", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        when(needListItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<NeedListItemResponse> response = service.replaceAll(List.of(request));

        assertEquals(1, response.size());
        assertEquals("CATEGORY", response.getFirst().type());
        assertEquals(0L, response.getFirst().categoryId());
        assertEquals("", response.getFirst().categoryName());
        assertEquals("", response.getFirst().name());
        assertEquals("URGENT", response.getFirst().urgency());
        assertEquals("Normal", response.getFirst().availabilityStatus());
    }
}
