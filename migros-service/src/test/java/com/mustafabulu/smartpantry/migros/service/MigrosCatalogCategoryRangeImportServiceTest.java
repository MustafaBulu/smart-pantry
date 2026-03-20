package com.mustafabulu.smartpantry.migros.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.smartpantry.migros.model.MigrosCatalogProduct;
import com.mustafabulu.smartpantry.migros.repository.MigrosCatalogProductRepository;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrosCatalogCategoryRangeImportServiceTest {

    @Mock
    private OkHttpClient httpClient;
    @Mock
    private MigrosCatalogProductRepository repository;

    private MigrosCatalogCategoryRangeImportService service;

    @BeforeEach
    void setUp() {
        service = new MigrosCatalogCategoryRangeImportService(httpClient, new ObjectMapper(), repository);
    }

    @Test
    void importFromCategoryRangeReturnsEmptyWhenSourceBlank() {
        var result = service.importFromCategoryRange(" ", 2, 4);

        assertEquals("MG", result.marketplaceCode());
        assertEquals(0, result.uniqueProductCount());
    }

    @Test
    void importFromCategoryRangeCollectsPagesAndPersistsCreatesAndUpdates() {
        MigrosCatalogProduct existing = new MigrosCatalogProduct();
        existing.setExternalId("mg-2");
        existing.setProductName("Old Product");
        when(repository.findByExternalIdIn(any())).thenReturn(List.of(existing));
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        stubResponses(Map.of(
                "https://www.migros.com.tr/rest/products/search?category-id=2&sayfa=1",
                """
                {"data":{
                  "pageCount":2,
                  "storeProductInfos":[
                    {
                      "id":"mg-1",
                      "name":"Sek Sut 1 L",
                      "brand":{"name":"Sek"},
                      "prettyName":"Sek Sut",
                      "category":{"id":2,"name":"Sut"},
                      "images":[{"urls":{"PRODUCT_LIST":"https://cdn.example.com/1.jpg"}}],
                      "status":"AVAILABLE",
                      "unit":"L",
                      "regularPrice":4590,
                      "shownPrice":4290,
                      "unitPrice":"(42,90 TL/L)",
                      "discountRate":7
                    }
                  ]
                }}
                """,
                "https://www.migros.com.tr/rest/products/search?category-id=2&sayfa=2",
                """
                {"data":{
                  "pageCount":2,
                  "storeProductInfos":[
                    {
                      "id":"mg-2",
                      "name":"Pinar Ayran 1 L",
                      "brand":{"name":"Pinar"},
                      "prettyName":"Pinar Ayran",
                      "category":{"id":2,"name":"Ayran"},
                      "images":[{"urls":{"PRODUCT_LIST":"https://cdn.example.com/2.jpg"}}],
                      "status":"AVAILABLE",
                      "unit":"L",
                      "regularPrice":5590,
                      "shownPrice":5090,
                      "unitPrice":"(50,90 TL/L)",
                      "discountRate":9
                    }
                  ]
                }}
                """
        ));

        var result = service.importFromCategoryRange(
                "https://www.migros.com.tr/rest/products/search",
                2,
                2
        );

        assertEquals("MG", result.marketplaceCode());
        assertEquals(1, result.categoryCount());
        assertEquals(2, result.totalPageCount());
        assertEquals(2, result.totalCollectedProductCount());
        assertEquals(2, result.uniqueProductCount());
        assertEquals(1, result.createdCount());
        assertEquals(1, result.updatedCount());

        ArgumentCaptor<List<MigrosCatalogProduct>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<MigrosCatalogProduct> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertTrue(saved.stream().anyMatch(product ->
                "mg-1".equals(product.getExternalId()) &&
                        "Sek Sut 1 L".equals(product.getProductName()) &&
                        0 == new BigDecimal("45.90").compareTo(product.getRegularPrice())
        ));
        assertTrue(saved.stream().anyMatch(product ->
                "mg-2".equals(product.getExternalId()) &&
                        "Pinar Ayran 1 L".equals(product.getProductName()) &&
                        0 == new BigDecimal("50.90").compareTo(product.getShownPrice())
        ));
    }

    @Test
    void importFromCategoryRangeStopsWhenUrlInvalid() {
        var result = service.importFromCategoryRange("not-a-url", 2, 3);

        assertEquals(2, result.categoryCount());
        assertEquals(0, result.totalPageCount());
        assertEquals(0, result.uniqueProductCount());
    }

    private void stubResponses(Map<String, String> responsesByUrl) {
        when(httpClient.newCall(any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            String body = responsesByUrl.get(request.url().toString());
            if (body == null) {
                throw new AssertionError("Unexpected URL: " + request.url());
            }
            Call call = org.mockito.Mockito.mock(Call.class);
            when(call.execute()).thenReturn(new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create(body, MediaType.get("application/json")))
                    .build());
            return call;
        });
    }
}
