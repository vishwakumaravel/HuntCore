package com.huntcore.backendapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PublicApiWebConfiguration implements WebMvcConfigurer {

    private final PublicApiProperties publicApiProperties;

    public PublicApiWebConfiguration(PublicApiProperties publicApiProperties) {
        this.publicApiProperties = publicApiProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] allowedOrigins = java.util.Arrays.stream(publicApiProperties.getAllowedOrigin().split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toArray(String[]::new);

        if (allowedOrigins.length == 0) {
            allowedOrigins = new String[] {"*"};
        }

        registry.addMapping("/api/v1/public/**")
            .allowedOriginPatterns(allowedOrigins)
            .allowedMethods("GET", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600);
    }
}
