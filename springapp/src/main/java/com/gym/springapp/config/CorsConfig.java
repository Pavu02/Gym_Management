package com.gym.springapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.frontend-url:http://localhost:8081}")
    private String frontendUrl;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = new ArrayList<>();

        if (frontendUrl != null && !frontendUrl.trim().isEmpty()) {
            for (String origin : frontendUrl.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    if (trimmed.endsWith("/")) {
                        trimmed = trimmed.substring(0, trimmed.length() - 1);
                    }
                    if (!origins.contains(trimmed)) {
                        origins.add(trimmed);
                    }
                }
            }
        }

        // Local development fallbacks
        for (String localOrigin : List.of("http://localhost:8081", "http://localhost:4200", "http://127.0.0.1:8081",
                "http://127.0.0.1:4200")) {
            if (!origins.contains(localOrigin)) {
                origins.add(localOrigin);
            }
        }

        registry.addMapping("/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
