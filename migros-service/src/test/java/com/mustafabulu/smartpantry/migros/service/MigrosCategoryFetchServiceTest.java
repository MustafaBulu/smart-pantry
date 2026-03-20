package com.mustafabulu.smartpantry.migros.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.smartpantry.common.config.MarketplaceUrlProperties;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.service.MarketplaceCatalogUrlFetchService;
import com.mustafabulu.smartpantry.common.service.MarketplaceCategoryFetchService;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrosCategoryFetchServiceTest {

    @Mock
    private OkHttpClient httpClient;
    @Mock
    private MigrosCatalogProductRepository repository;

    private MigrosCategoryFetchService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MarketplaceUrlProperties urlProperties = new MarketplaceUrlProperties();
        urlProperties.setMigrosPrefix("https://details.example.com/products/");
        urlProperties.setMigrosSuffix("");
        service = new MigrosCategoryFetchService(httpClient, objectMapper, urlProperties, repository);
    }

    @Test
    void listSeedCategoryKeysParsesPrettyNamesFromArray() throws Exception {
        stubResponses(Map.of(
                "https://www.migros.com.tr/rest/categories/top-level?reid=1772491546503000001",
                """
                {"data":[
                  {"prettyName":"Sut ve Kahvaltilik"},
                  {"prettyName":"Atistirmalik"},
                  {"prettyName":"Sut ve Kahvaltilik"}
                ]}
                """
        ));

        List<String> result = service.listSeedCategoryKeys();

        assertEquals(List.of("Sut ve Kahvaltilik", "Atistirmalik"), result);
    }

    @Test
    void fetchAllByUrlCollectsPagesAndKeepsUniqueProducts() throws Exception {
        stubResponses(Map.of(
                "https://www.migros.com.tr/rest/search?page=test&sayfa=1",
                """
                {"data":{"searchInfo":{"pageCount":2,"storeProductInfos":[
                  {
                    "id":"mg-1",
                    "name":"Sek Sut 1 L",
                    "brand":{"name":"Sek"},
                    "socialProofInfo":{"categoryName":"Sut"},
                    "price":42.90,
                    "shownPrice":39.90,
                    "images":[{"urls":{"PRODUCT_LIST":"https://cdn.example.com/1.jpg"}}],
                    "propertyInfosMap":{"MAIN":[{"customId":"netKg","name":"Net Miktar","value":"1 lt"}]}
                  }
                ]}}}
                """,
                "https://www.migros.com.tr/rest/search?page=test&sayfa=2",
                """
                {"data":{"searchInfo":{"pageCount":2,"storeProductInfos":[
                  {
                    "id":"mg-1",
                    "name":"Sek Sut 1 L",
                    "brand":{"name":"Sek"},
                    "socialProofInfo":{"categoryName":"Sut"},
                    "price":42.90,
                    "shownPrice":39.90,
                    "images":[{"urls":{"PRODUCT_LIST":"https://cdn.example.com/1.jpg"}}],
                    "propertyInfosMap":{"MAIN":[{"customId":"netKg","name":"Net Miktar","value":"1 lt"}]}
                  },
                  {
                    "id":"mg-2",
                    "name":"Pinar Ayran 6 li 200 ml",
                    "brand":{"name":"Pinar"},
                    "category":{"name":"Icecek"},
                    "price":55.90,
                    "shownPrice":50.90,
                    "images":[{"urls":{"PRODUCT_LIST":"https://cdn.example.com/2.jpg"}}],
                    "propertyInfosMap":{"MAIN":[{"customId":"netKg","name":"Net Miktar","value":"200 ml"}]}
                  }
                ]}}}
                """
        ));

        List<MarketplaceCatalogUrlFetchService.CatalogUrlProductCandidate> result =
                service.fetchAllByUrl("https://www.migros.com.tr/rest/search?page=test");

        assertEquals(2, result.size());
        assertEquals("Sut", result.getFirst().categoryName());
        assertEquals("mg-1", result.getFirst().candidate().externalId());
        assertEquals(6, result.get(1).candidate().packCount());
    }

    @Test
    void fetchByCategoryBuildsCandidateFromSearchAndDetailsResponses() throws Exception {
        stubResponses(Map.of(
                "https://www.migros.com.tr/rest/search/screens/products?q=sut&reid=1770560051246000048",
                """
                {"data":{"searchInfo":{"storeProductInfos":[
                  {
                    "id":"mg-7",
                    "name":"Sek Sut 2 Al 1 Ode 1 L",
                    "brand":{"name":"Sek"},
                    "price":45.90,
                    "shownPrice":39.90,
                    "unitPrice":"(39,90 TL/L)",
                    "images":[{"urls":{"PRODUCT_LIST":"https://cdn.example.com/7.jpg"}}],
                    "crmDiscountTags":{"crmDiscountTags":[{"tag":"60 TL Sepette 35,90 TL"},{"tag":"2 Al 1 Ode"}]}
                  }
                ]}}}
                """,
                "https://details.example.com/products/mg-7",
                """
                {"data":{"storeProductInfoDTO":{
                  "propertyInfosMap":{"MAIN":[{"customId":"netKg","name":"Net Miktar","value":"1 lt"}]}
                }}}
                """
        ));

        List<MarketplaceCategoryFetchService.MarketplaceProductCandidate> result = service.fetchByCategory("sut");

        assertEquals(1, result.size());
        MarketplaceCategoryFetchService.MarketplaceProductCandidate candidate = result.getFirst();
        assertEquals(Marketplace.MG, candidate.marketplace());
        assertEquals("mg-7", candidate.externalId());
        assertEquals("Sek", candidate.brandName());
        assertEquals(0, new BigDecimal("39.90").compareTo(candidate.moneyPrice()));
        assertEquals(0, new BigDecimal("60").compareTo(candidate.basketDiscountThreshold()));
        assertEquals(0, new BigDecimal("35.90").compareTo(candidate.basketDiscountPrice()));
        assertEquals(2, candidate.campaignBuyQuantity());
        assertEquals(1, candidate.campaignPayQuantity());
        assertEquals("ml", candidate.unit());
        assertEquals(1000, candidate.unitValue());
        assertEquals(0, new BigDecimal("22.95").compareTo(candidate.effectivePrice()));
    }

    @Test
    void fetchByCategoryReturnsEmptyWhenSearchFails() throws Exception {
        stubFailure("https://www.migros.com.tr/rest/search/screens/products?q=sut&reid=1770560051246000048", 500);

        assertTrue(service.fetchByCategory("sut").isEmpty());
    }

    @Test
    void fetchAllByUrlParsesCompactPackPatterns() throws Exception {
        stubResponses(Map.of(
                "https://www.migros.com.tr/rest/search?page=test&sayfa=1",
                """
                {"data":{"searchInfo":{"pageCount":1,"storeProductInfos":[
                  {
                    "id":"mg-compact",
                    "name":"Pinar Sut 2x1,5 lt",
                    "brand":{"name":"Pinar"},
                    "socialProofInfo":{"categoryName":"Sut"},
                    "price":89.90,
                    "shownPrice":84.90,
                    "images":[{"urls":{"PRODUCT_LIST":"https://cdn.example.com/compact.jpg"}}],
                    "propertyInfosMap":{"MAIN":[{"customId":"netKg","name":"Net Miktar","value":"1,5 lt"}]}
                  },
                  {
                    "id":"mg-listed",
                    "name":"Ayran 2li",
                    "brand":{"name":"Pinar"},
                    "socialProofInfo":{"categoryName":"Icecek"},
                    "price":39.90,
                    "shownPrice":37.90,
                    "images":[{"urls":{"PRODUCT_LIST":"https://cdn.example.com/listed.jpg"}}],
                    "propertyInfosMap":{"MAIN":[{"customId":"netKg","name":"Net Miktar","value":"200 ml"}]}
                  }
                ]}}}
                """
        ));

        List<MarketplaceCatalogUrlFetchService.CatalogUrlProductCandidate> result =
                service.fetchAllByUrl("https://www.migros.com.tr/rest/search?page=test");

        assertEquals(2, result.size());
        assertEquals(2, result.getFirst().candidate().packCount());
        assertEquals(2, result.get(1).candidate().packCount());
    }

    private void stubResponses(Map<String, String> responsesByUrl) {
        when(httpClient.newCall(any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            String body = responsesByUrl.get(request.url().toString());
            if (body == null) {
                throw new AssertionError("Unexpected URL: " + request.url());
            }
            Call call = org.mockito.Mockito.mock(Call.class);
            when(call.execute()).thenReturn(successfulResponse(request, body));
            return call;
        });
    }

    private void stubFailure(String url, int code) {
        when(httpClient.newCall(any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            if (!url.equals(request.url().toString())) {
                throw new AssertionError("Unexpected URL: " + request.url());
            }
            Call call = org.mockito.Mockito.mock(Call.class);
            when(call.execute()).thenReturn(failedResponse(request, code));
            return call;
        });
    }

    private Response successfulResponse(Request request, String body) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .build();
    }

    private Response failedResponse(Request request, int status) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("FAIL")
                .body(ResponseBody.create("", MediaType.get("application/json")))
                .build();
    }
}
