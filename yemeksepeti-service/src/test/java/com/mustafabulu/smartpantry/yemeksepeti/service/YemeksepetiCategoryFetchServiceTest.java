package com.mustafabulu.smartpantry.yemeksepeti.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.smartpantry.common.enums.Marketplace;
import com.mustafabulu.smartpantry.common.service.MarketplaceCategoryFetchService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YemeksepetiCategoryFetchServiceTest {

    @Test
    void fetchByCategoryReturnsEmptyForBlankInput() {
        YemeksepetiCategoryFetchService service = new YemeksepetiCategoryFetchService(mock(HttpClient.class), new ObjectMapper());

        assertTrue(service.fetchByCategory(" ").isEmpty());
    }

    @Test
    void fetchByCategoryParsesCandidateMetadataAndBrandInference() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = stringResponse(200, """
                {
                  "data":{
                    "searchProducts":{
                      "products":{
                        "items":[
                          {
                            "payload":{
                              "productID":"ys-1",
                              "name":"Bundle Coca Cola 2x1 L",
                              "price":24.95,
                              "urls":["https://cdn.example.com/1.jpg"],
                              "attributes":[
                                {"key":"contentsValue","value":"1"},
                                {"key":"contentsUnit","value":"L"},
                                {"key":"numberOfUnits","value":"2"}
                              ]
                            }
                          },
                          {
                            "payload":{
                              "productID":"ys-2",
                              "name":"La Lorraine Croissant",
                              "price":15.50,
                              "urls":["https://cdn.example.com/2.jpg"],
                              "attributes":[
                                {"key":"brandName","value":"La Lorraine"},
                                {"key":"weightValue","value":"250"},
                                {"key":"weightUnit","value":"g"}
                              ]
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        YemeksepetiCategoryFetchService service = new YemeksepetiCategoryFetchService(httpClient, new ObjectMapper());

        List<MarketplaceCategoryFetchService.MarketplaceProductCandidate> result = service.fetchByCategory("cola");

        assertEquals(2, result.size());
        MarketplaceCategoryFetchService.MarketplaceProductCandidate cola = result.getFirst();
        assertEquals(Marketplace.YS, cola.marketplace());
        assertEquals("Coca-Cola", cola.brandName());
        assertEquals(new BigDecimal("49.90"), cola.price());
        assertEquals("ml", cola.unit());
        assertEquals(2000, cola.unitValue());
        assertEquals(2, cola.packCount());

        MarketplaceCategoryFetchService.MarketplaceProductCandidate pastry = result.get(1);
        assertEquals("La Lorraine", pastry.brandName());
        assertEquals("g", pastry.unit());
        assertEquals(250, pastry.unitValue());
        assertNull(pastry.packCount());
    }

    @Test
    void fetchByCategoryStopsWhenSecondPageAddsNoNewCandidates() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> firstResponse = stringResponse(200, pageWithRepeatedProduct("ys-1"));
        HttpResponse<String> secondResponse = stringResponse(200, pageWithRepeatedProduct("ys-1"));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(firstResponse, secondResponse);

        YemeksepetiCategoryFetchService service = new YemeksepetiCategoryFetchService(httpClient, new ObjectMapper());

        List<MarketplaceCategoryFetchService.MarketplaceProductCandidate> result = service.fetchByCategory("cola");

        assertEquals(1, result.size());
    }

    @Test
    void fetchByCategoryReturnsEmptyWhenResponseIsNotSuccessful() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = stringResponse(500, "{\"errors\":[{\"message\":\"boom\"}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        YemeksepetiCategoryFetchService service = new YemeksepetiCategoryFetchService(httpClient, new ObjectMapper());

        assertTrue(service.fetchByCategory("cola").isEmpty());
    }

    @Test
    void fetchByCategoryParsesCompactBundleMultiplierFromName() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = stringResponse(200, """
                {
                  "data":{
                    "searchProducts":{
                      "products":{
                        "items":[
                          {
                            "payload":{
                              "productID":"ys-compact",
                              "name":"Bundle Coca Cola 3x1 L",
                              "price":24.95,
                              "urls":["https://cdn.example.com/compact.jpg"],
                              "attributes":[
                                {"key":"contentsValue","value":"1"},
                                {"key":"contentsUnit","value":"L"}
                              ]
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        YemeksepetiCategoryFetchService service = new YemeksepetiCategoryFetchService(httpClient, new ObjectMapper());

        List<MarketplaceCategoryFetchService.MarketplaceProductCandidate> result = service.fetchByCategory("cola");

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("74.85"), result.getFirst().price());
    }

    @Test
    void fetchByCategoryUsesDeclaredNumberOfUnitsWhenPackSuffixContainsApostrophe() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = stringResponse(200, """
                {
                  "data":{
                    "searchProducts":{
                      "products":{
                        "items":[
                          {
                            "payload":{
                              "productID":"ys-apostrophe",
                              "name":"Pinar Ayran 2'li 200 ml",
                              "price":19.95,
                              "urls":["https://cdn.example.com/apostrophe.jpg"],
                              "attributes":[
                                {"key":"contentsValue","value":"200"},
                                {"key":"contentsUnit","value":"ml"},
                                {"key":"numberOfUnits","value":"2"}
                              ]
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        YemeksepetiCategoryFetchService service = new YemeksepetiCategoryFetchService(httpClient, new ObjectMapper());

        List<MarketplaceCategoryFetchService.MarketplaceProductCandidate> result = service.fetchByCategory("ayran");

        assertEquals(1, result.size());
        assertEquals(2, result.getFirst().packCount());
        assertEquals("ml", result.getFirst().unit());
        assertEquals(400, result.getFirst().unitValue());
        assertEquals(new BigDecimal("19.95"), result.getFirst().price());
    }

    private HttpResponse<String> stringResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(java.util.Map.of(), (left, right) -> true));
        when(response.request()).thenReturn(HttpRequest.newBuilder().uri(java.net.URI.create("https://example.com")).build());
        when(response.previousResponse()).thenReturn(Optional.empty());
        return response;
    }

    private String pageWithRepeatedProduct(String externalId) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 12; i += 1) {
            items.append("""
                    {
                      "payload":{
                        "productID":"%s",
                        "name":"Coca Cola 1 L",
                        "price":20.0,
                        "urls":["https://cdn.example.com/repeated.jpg"],
                        "attributes":[
                          {"key":"contentsValue","value":"1"},
                          {"key":"contentsUnit","value":"L"}
                        ]
                      }
                    }
                    """.formatted(externalId));
            if (i < 11) {
                items.append(",");
            }
        }
        return """
                {
                  "data":{
                    "searchProducts":{
                      "products":{
                        "items":[%s]
                      }
                    }
                  }
                }
                """.formatted(items);
    }
}
