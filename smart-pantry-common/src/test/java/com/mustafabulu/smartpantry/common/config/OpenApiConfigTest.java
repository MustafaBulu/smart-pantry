package com.mustafabulu.smartpantry.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void customOpenApiIncludesParsedServers() {
        OpenAPI openAPI = config.customOpenApi(
                "Smart Pantry API",
                "Service endpoints",
                "https://api.example.com|Production; https://staging.example.com | Staging ; ;"
        );

        assertEquals("Smart Pantry API", openAPI.getInfo().getTitle());
        assertEquals("Service endpoints", openAPI.getInfo().getDescription());
        assertEquals(2, openAPI.getServers().size());
        assertEquals("https://api.example.com", openAPI.getServers().getFirst().getUrl());
        assertEquals("Production", openAPI.getServers().getFirst().getDescription());
        assertEquals("https://staging.example.com", openAPI.getServers().get(1).getUrl());
        assertEquals("Staging", openAPI.getServers().get(1).getDescription());
    }

    @Test
    void customOpenApiSkipsServersWhenPropertyBlank() {
        OpenAPI openAPI = config.customOpenApi("Title", "Description", " ");

        assertTrue(openAPI.getServers() == null || openAPI.getServers().isEmpty());
    }
}
