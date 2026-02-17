package com.mustafabulu.smartpantry.migros.service;

import com.mustafabulu.smartpantry.core.exception.SPException;
import com.mustafabulu.smartpantry.migros.model.MigrosProductDetails;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrosScraperServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchProductDetailsParsesResponse() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/product", exchange -> {
            String body = """
                    {"successful":true,
                     "data":{
                       "storeProductInfoDTO":{
                         "name":"Chips",
                         "salePrice":1999,
                         "shownPrice":1799,
                         "description":"Net Miktar</strong><br> 150 g",
                         "brand":{"name":"Brand"},
                         "crmDiscountTags":{
                           "crmDiscountTags":[
                             {"tag":"50 TL Sepette 17,95 TL!"},
                             {"tag":"2 Öde 1'i Money Hediye"}
                           ]
                         }
                       }
                     }
                    }
                    """;
            exchange.sendResponseHeaders(200, body.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        });
        server.start();

        MigrosScraperService service = new MigrosScraperService();
        String url = "http://localhost:" + server.getAddress().getPort() + "/product";

        MigrosProductDetails details = service.fetchProductDetails(url);

        assertNotNull(details);
        assertEquals("Chips", details.name());
        assertEquals(19.99, details.currentPrice());
        assertEquals("g", details.unit());
        assertEquals(150, details.unitValue());
        assertEquals("Brand", details.brand());
        assertEquals("17.99", details.moneyPrice().stripTrailingZeros().toPlainString());
        assertEquals("50", details.basketDiscountThreshold().stripTrailingZeros().toPlainString());
        assertEquals("17.95", details.basketDiscountPrice().stripTrailingZeros().toPlainString());
        assertEquals(2, details.campaignBuyQuantity());
        assertEquals(1, details.campaignPayQuantity());
        assertEquals("10", details.effectivePrice().stripTrailingZeros().toPlainString());
    }

    @Test
    void fetchProductDetailsParsesDiscountTagFromArrayShape() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/product", exchange -> {
            String body = """
                    {"successful":true,
                     "data":{
                       "storeProductInfoDTO":{
                         "name":"Chips",
                         "salePrice":1999,
                         "shownPrice":1899,
                         "description":"Net Miktar</strong><br> 150 g",
                         "brand":{"name":"Brand"},
                         "crmDiscountTags":[
                           {"tag":"50 TL Sepette 17,95 TL!"}
                         ]
                       }
                     }
                    }
                    """;
            exchange.sendResponseHeaders(200, body.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        });
        server.start();

        MigrosScraperService service = new MigrosScraperService();
        String url = "http://localhost:" + server.getAddress().getPort() + "/product";

        MigrosProductDetails details = service.fetchProductDetails(url);

        assertNotNull(details);
        assertEquals("18.99", details.moneyPrice().stripTrailingZeros().toPlainString());
        assertEquals("50", details.basketDiscountThreshold().stripTrailingZeros().toPlainString());
        assertEquals("17.95", details.basketDiscountPrice().stripTrailingZeros().toPlainString());
        assertNull(details.campaignBuyQuantity());
        assertNull(details.campaignPayQuantity());
        assertNull(details.effectivePrice());
    }

    @Test
    void fetchProductDetailsThrowsWhenSuccessfulFalse() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/product", exchange -> {
            String body = """
                    {"successful":false}
                    """;
            exchange.sendResponseHeaders(200, body.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        });
        server.start();

        MigrosScraperService service = new MigrosScraperService();
        String url = "http://localhost:" + server.getAddress().getPort() + "/product";

        assertThrows(SPException.class, () -> service.fetchProductDetails(url));
    }
}
