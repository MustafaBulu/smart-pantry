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
                         "description":"Net Miktar</strong><br> 150 g",
                         "brand":{"name":"Brand"}
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