package com.mustafabulu.smartpantry.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOriginPatterns;

    public WebConfig(@Value("${app.cors.allowed-origins:http://localhost:3000}") String allowedOriginsProperty) {
        this.allowedOriginPatterns = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/mg/v3/api-docs").setViewName("forward:/v3/api-docs");
        registry.addViewController("/mg/v3/api-docs/swagger-config").setViewName("forward:/v3/api-docs/swagger-config");
        registry.addViewController("/ys/v3/api-docs").setViewName("forward:/v3/api-docs");
        registry.addViewController("/ys/v3/api-docs/swagger-config").setViewName("forward:/v3/api-docs/swagger-config");
    }
}
