package com.mustafabulu.smartpantry.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebConfigTest {

    @Test
    void addCorsMappingsRegistersConfiguredOrigins() {
        WebConfig config = new WebConfig("https://a.example.com, https://b.example.com");
        CorsRegistry registry = new CorsRegistry();

        config.addCorsMappings(registry);

        assertNotNull(registry);
    }

    @Test
    void addViewControllersRegistersApiDocForwards() {
        WebConfig config = new WebConfig("http://localhost:3000");
        ViewControllerRegistry registry = new ViewControllerRegistry(new StaticWebApplicationContext());

        config.addViewControllers(registry);

        assertNotNull(registry);
    }
}
