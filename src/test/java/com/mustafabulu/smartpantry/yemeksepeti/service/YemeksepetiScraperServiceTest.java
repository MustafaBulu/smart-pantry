package com.mustafabulu.smartpantry.yemeksepeti.service;

import com.mustafabulu.smartpantry.core.exception.SPException;
import com.mustafabulu.smartpantry.yemeksepeti.model.YemeksepetiProductDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YemeksepetiScraperServiceTest {

    @Test
    void fetchProductDetailsParsesPreloadedState() {
        WebDriver driver = mock(WebDriver.class);
        String json = """
                {"page":{"productDetail":{"product":{
                  "name":"Chips",
                  "price":12.5,
                  "originalPrice":15.0,
                  "strikethroughPrice":0.0,
                  "stockAmount":10,
                  "activeCampaigns":[{"endTime":"2026-02-01"}],
                  "productInfos":[{"title":"Net Miktar","values":["150 g"]}]
                }}}}
                """;
        String html = "<script>window.__PRELOADED_STATE__=" + json + ";</script>";
        when(driver.getPageSource()).thenReturn(html);

        YemeksepetiScraperService service = new YemeksepetiScraperService(() -> driver);

        YemeksepetiProductDetails details = service.fetchProductDetails("http://example");

        assertNotNull(details);
        assertEquals("Chips", details.name());
        assertEquals(12.5, details.currentPrice());
        assertEquals("g", details.unit());
        assertEquals(150, details.unitValue());
        verify(driver).quit();
    }

    @Test
    void fetchProductDetailsReturnsNullWhenProductMissing() {
        WebDriver driver = mock(WebDriver.class);
        String json = "{\"page\":{}}";
        String html = "<script>window.__PRELOADED_STATE__=" + json + ";</script>";
        when(driver.getPageSource()).thenReturn(html);

        YemeksepetiScraperService service = new YemeksepetiScraperService(() -> driver);

        YemeksepetiProductDetails details = service.fetchProductDetails("http://example");

        assertNull(details);
        verify(driver).quit();
    }

    @ParameterizedTest
    @MethodSource("netAmountCases")
    void fetchProductDetailsParsesNetAmount(
            String json,
            String expectedUnit,
            Integer expectedUnitValue,
            Double expectedMultiplier
    ) {
        WebDriver driver = mock(WebDriver.class);
        String html = "<script>window.__PRELOADED_STATE__=" + json + ";</script>";
        when(driver.getPageSource()).thenReturn(html);

        YemeksepetiScraperService service = new YemeksepetiScraperService(() -> driver);

        YemeksepetiProductDetails details = service.fetchProductDetails("http://example");

        assertNotNull(details);
        assertEquals(expectedUnit, details.unit());
        assertEquals(expectedUnitValue, details.unitValue());
        if (expectedMultiplier != null) {
            assertEquals(expectedMultiplier, details.priceMultiplier(), 0.0001);
        }
        verify(driver).quit();
    }

    @Test
    void fetchProductDetailsPrefersProductInfosOverDetails() {
        WebDriver driver = mock(WebDriver.class);
        String json = """
                {"page":{"productDetail":{"product":{
                  "name":"Rice",
                  "price":30.0,
                  "originalPrice":0.0,
                  "strikethroughPrice":0.0,
                  "stockAmount":5,
                  "activeCampaigns":[{"endTime":"2026-02-01"}],
                  "productInfos":[{"title":"Net Miktar","values":["2 kg"]}],
                  "details":{"productInfos":[{"title":"Net Miktar","values":["500 g"]}]}
                }}}}
                """;
        String html = "<script>window.__PRELOADED_STATE__=" + json + ";</script>";
        when(driver.getPageSource()).thenReturn(html);

        YemeksepetiScraperService service = new YemeksepetiScraperService(() -> driver);

        YemeksepetiProductDetails details = service.fetchProductDetails("http://example");

        assertNotNull(details);
        assertEquals("g", details.unit());
        assertEquals(500, details.unitValue());
        verify(driver).quit();
    }

    @Test
    void fetchProductDetailsHandlesUndefinedValues() {
        WebDriver driver = mock(WebDriver.class);
        String json = """
                {"page":{"productDetail":{"product":{
                  "name":"Tea",
                  "price":15.0,
                  "originalPrice":0.0,
                  "strikethroughPrice":0.0,
                  "stockAmount":5,
                  "activeCampaigns":undefined,
                  "productInfos":[{"title":"Net Miktar","values":["100 g"]}]
                }}}}
                """;
        String html = "<script>window.__PRELOADED_STATE__=" + json + ";</script>";
        when(driver.getPageSource()).thenReturn(html);

        YemeksepetiScraperService service = new YemeksepetiScraperService(() -> driver);

        YemeksepetiProductDetails details = service.fetchProductDetails("http://example");

        assertNotNull(details);
        assertEquals("g", details.unit());
        verify(driver).quit();
    }

    @Test
    void fetchProductDetailsThrowsOnInvalidJson() {
        WebDriver driver = mock(WebDriver.class);
        String html = "<script>window.__PRELOADED_STATE__=not-json;</script>";
        when(driver.getPageSource()).thenReturn(html);

        YemeksepetiScraperService service = new YemeksepetiScraperService(() -> driver);

        assertThrows(SPException.class, () -> service.fetchProductDetails("http://example"));
        verify(driver).quit();
    }

    @Test
    void fetchProductDetailsThrowsOnInterruptedSleep() {
        WebDriver driver = mock(WebDriver.class);
        when(driver.getPageSource()).thenReturn("<html></html>");
        YemeksepetiScraperService service = new YemeksepetiScraperService(() -> driver);

        Thread.currentThread().interrupt();
        try {
            assertThrows(SPException.class, () -> service.fetchProductDetails("http://example"));
        } finally {
            boolean cleared = Thread.interrupted();
            assertTrue(cleared);
        }
        verify(driver).quit();
    }

    @Test
    void fetchProductDetailsRetriesUntilPreloadedFound() {
        WebDriver driver = mock(WebDriver.class);
        String json = "{\"page\":{\"productDetail\":{\"product\":{\"name\":\"Tea\",\"price\":10.0,\"originalPrice\":0.0,\"strikethroughPrice\":0.0,\"stockAmount\":1,\"activeCampaigns\":[],\"productInfos\":[{\"title\":\"Net Miktar\",\"values\":[\"100 g\"]}]}}}}";
        String htmlWithout = "<html></html>";
        String htmlWith = "<script>window.__PRELOADED_STATE__=" + json + ";</script>";
        when(driver.getPageSource()).thenReturn(htmlWithout, htmlWith);

        YemeksepetiScraperService service = new YemeksepetiScraperService(() -> driver);

        YemeksepetiProductDetails details = service.fetchProductDetails("http://example");

        assertNotNull(details);
        assertEquals("Tea", details.name());
        verify(driver).quit();
    }

    @Test
    void fetchProductDetailsReturnsNullWhenPreloadedNeverAppears() {
        WebDriver driver = mock(WebDriver.class);
        when(driver.getPageSource()).thenReturn("<html></html>");

        YemeksepetiScraperService service = new YemeksepetiScraperService(() -> driver);

        YemeksepetiProductDetails details = service.fetchProductDetails("http://example");

        assertNull(details);
        verify(driver).quit();
    }

    @Test
    void fetchProductDetailsThrowsWhenDriverFails() {
        WebDriver driver = mock(WebDriver.class);
        when(driver.getPageSource()).thenThrow(new RuntimeException("boom"));
        YemeksepetiScraperService service = new YemeksepetiScraperService(() -> driver);

        assertThrows(SPException.class, () -> service.fetchProductDetails("http://example"));
        verify(driver).quit();
    }

    @Test
    void fetchProductDetailsUsesStrikethroughWhenOriginalZero() {
        WebDriver driver = mock(WebDriver.class);
        String json = """
                {"page":{"productDetail":{"product":{
                  "name":"Cookies",
                  "price":10.0,
                  "originalPrice":0.0,
                  "strikethroughPrice":12.0,
                  "stockAmount":5,
                  "activeCampaigns":[{"endTime":"2026-02-10"}],
                  "productInfos":[{"title":"Net Miktar","values":["100 g"]}]
                }}}}
                """;
        String html = "<script>window.__PRELOADED_STATE__=" + json + ";</script>";
        when(driver.getPageSource()).thenReturn(html);

        YemeksepetiScraperService service = new YemeksepetiScraperService(() -> driver);

        YemeksepetiProductDetails details = service.fetchProductDetails("http://example");

        assertNotNull(details);
        assertEquals(12.0, details.originalPrice());
        assertEquals("2026-02-10", details.campaignEndTime());
        verify(driver).quit();
    }

    @Test
    void fetchProductDetailsUsesOriginalWhenPresent() {
        WebDriver driver = mock(WebDriver.class);
        String json = """
                {"page":{"productDetail":{"product":{
                  "name":"Chocolate",
                  "price":8.0,
                  "originalPrice":15.0,
                  "strikethroughPrice":12.0,
                  "stockAmount":5,
                  "activeCampaigns":[],
                  "productInfos":[{"title":"Net Miktar","values":["250 g"]}]
                }}}}
                """;
        String html = "<script>window.__PRELOADED_STATE__=" + json + ";</script>";
        when(driver.getPageSource()).thenReturn(html);

        YemeksepetiScraperService service = new YemeksepetiScraperService(() -> driver);

        YemeksepetiProductDetails details = service.fetchProductDetails("http://example");

        assertNotNull(details);
        assertEquals(15.0, details.originalPrice());
        verify(driver).quit();
    }

    @Test
    void buildOptionsCreatesHeadlessOptions() throws Exception {
        var method = YemeksepetiScraperService.class.getDeclaredMethod("buildOptions");
        method.setAccessible(true);
        ChromeOptions options = (ChromeOptions) method.invoke(null);

        assertNotNull(options);
    }

    private static Stream<Arguments> netAmountCases() {
        return Stream.of(
                Arguments.of("""
                        {"page":{"productDetail":{"product":{
                          "name":"Juice",
                          "price":20.0,
                          "originalPrice":0.0,
                          "strikethroughPrice":0.0,
                          "stockAmount":5,
                          "activeCampaigns":[],
                          "attributes":{"baseContentValue":1,"baseUnit":"l"}
                        }}}}
                        """, "ml", 1000, 1.0),
                Arguments.of("""
                        {"page":{"productDetail":{"product":{
                          "name":"Flour",
                          "price":25.0,
                          "originalPrice":0.0,
                          "strikethroughPrice":0.0,
                          "stockAmount":5,
                          "activeCampaigns":[],
                          "foodLabelling":{"productInfos":[{"title":"Net Miktar","values":["500 g"]}]}
                        }}}}
                        """, "g", 500, 2.0),
                Arguments.of("""
                        {"page":{"productDetail":{"product":{
                          "name":"Sugar",
                          "price":22.0,
                          "originalPrice":0.0,
                          "strikethroughPrice":0.0,
                          "stockAmount":5,
                          "activeCampaigns":[],
                          "infoList":{"productInfos":[{"title":"Net Miktar","values":["750 g"]}]}
                        }}}}
                        """, "g", 750, 1.3333),
                Arguments.of("""
                        {"page":{"productDetail":{"product":{
                          "name":"Rice",
                          "price":30.0,
                          "originalPrice":0.0,
                          "strikethroughPrice":0.0,
                          "stockAmount":5,
                          "activeCampaigns":[],
                          "productInfos":[{"title":"Net Miktar","values":["2 kg"]}]
                        }}}}
                        """, "g", 2000, 1.0),
                Arguments.of("""
                        {"page":{"productDetail":{"product":{
                          "name":"Milk",
                          "price":12.0,
                          "originalPrice":0.0,
                          "strikethroughPrice":0.0,
                          "stockAmount":5,
                          "activeCampaigns":[],
                          "productInfos":[{"title":"Net Miktar","values":[]}],
                          "attributes":{"baseContentValue":250,"baseUnit":"ml"}
                        }}}}
                        """, "ml", 250, 4.0),
                Arguments.of("""
                        {"page":{"productDetail":{"product":{
                          "name":"Oil",
                          "price":40.0,
                          "originalPrice":0.0,
                          "strikethroughPrice":0.0,
                          "stockAmount":5,
                          "activeCampaigns":[],
                          "productInfos":[{"title":"Net Miktar","values":["N/A"]}]
                        }}}}
                        """, null, null, null),
                Arguments.of("""
                        {"page":{"productDetail":{"product":{
                          "name":"Juice",
                          "price":18.0,
                          "originalPrice":0.0,
                          "strikethroughPrice":0.0,
                          "stockAmount":5,
                          "activeCampaigns":[],
                          "productInfos":[{"title":"Net Miktar","values":["250 ml"]}]
                        }}}}
                        """, "ml", 250, 4.0),
                Arguments.of("""
                        {"page":{"productDetail":{"product":{
                          "name":"Water",
                          "price":5.0,
                          "originalPrice":0.0,
                          "strikethroughPrice":0.0,
                          "stockAmount":5,
                          "activeCampaigns":[],
                          "productInfos":[{"title":"Net Miktar","values":["1,5 l"]}]
                        }}}}
                        """, "ml", 1500, 1.0),
                Arguments.of("""
                        {"page":{"productDetail":{"product":{
                          "name":"Nuts",
                          "price":20.0,
                          "originalPrice":0.0,
                          "strikethroughPrice":0.0,
                          "stockAmount":5,
                          "activeCampaigns":[],
                          "productInfos":[{"title":"Net Miktar","values":["0.5 kg"]}]
                        }}}}
                        """, "g", 500, 2.0),
                Arguments.of("""
                        {"page":{"productDetail":{"product":{
                          "name":"Spice",
                          "price":6.0,
                          "originalPrice":0.0,
                          "strikethroughPrice":0.0,
                          "stockAmount":5,
                          "activeCampaigns":[],
                          "productInfos":[{"title":"Net Miktar","values":["500 g"]}]
                        }}}}
                        """, "g", 500, 2.0),
                Arguments.of("""
                        {"page":{"productDetail":{"product":{
                          "name":"Salt",
                          "price":4.0,
                          "originalPrice":0.0,
                          "strikethroughPrice":0.0,
                          "stockAmount":5,
                          "activeCampaigns":[],
                          "attributes":{"baseContentValue":0,"baseUnit":""}
                        }}}}
                        """, null, null, null),
                Arguments.of("""
                        {"page":{"productDetail":{"product":{
                          "name":"Pasta",
                          "price":9.0,
                          "originalPrice":0.0,
                          "strikethroughPrice":0.0,
                          "stockAmount":5,
                          "activeCampaigns":[],
                          "productInfos":[{"title":"Net Miktar","values":["1500 g"]}]
                        }}}}
                        """, "g", 1500, 1.0),
                Arguments.of("""
                        {"page":{"productDetail":{"product":{
                          "name":"Water",
                          "price":7.0,
                          "originalPrice":0.0,
                          "strikethroughPrice":0.0,
                          "stockAmount":5,
                          "activeCampaigns":[],
                          "productInfos":[{"title":"Net Miktar","values":["2 l"]}]
                        }}}}
                        """, "ml", 2000, 1.0)
        );
    }
}
