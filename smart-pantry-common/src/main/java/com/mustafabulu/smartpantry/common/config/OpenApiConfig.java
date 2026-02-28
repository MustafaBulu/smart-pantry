package com.mustafabulu.smartpantry.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenApi(
            @Value("${docs.title:Smart Pantry API}") String title,
            @Value("${docs.description:Smart Pantry service endpoints}") String description,
            @Value("${docs.servers:}") String serversProperty
    ) {
        OpenAPI openAPI = new OpenAPI().info(
                new Info()
                        .title(title)
                        .description(description)
                        .version("v1")
        );
        List<Server> servers = parseServers(serversProperty);
        if (!servers.isEmpty()) {
            openAPI.setServers(servers);
        }
        return openAPI;
    }

    private List<Server> parseServers(String serversProperty) {
        if (serversProperty == null || serversProperty.isBlank()) {
            return List.of();
        }
        List<Server> servers = new ArrayList<>();
        String[] entries = serversProperty.split(";");
        for (String entry : entries) {
            String trimmed = entry == null ? "" : entry.trim();
            if (!trimmed.isBlank()) {
                String[] parts = trimmed.split("\\|", 2);
                String url = parts[0].trim();
                if (!url.isBlank()) {
                    Server server = new Server().url(url);
                    if (parts.length > 1 && !parts[1].trim().isBlank()) {
                        server.setDescription(parts[1].trim());
                    }
                    servers.add(server);
                }
            }
        }
        return servers;
    }
}
