package org.hermesprd.workbench.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    static final String[] API_ALLOWED_METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"};

    private final List<String> allowedOrigins;

    public WebConfig(@Value("${PRD_CORS_ALLOWED_ORIGINS:http://127.0.0.1:5174,http://localhost:5174}") String origins) {
        this.allowedOrigins = parseAllowedOrigins(origins);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods(API_ALLOWED_METHODS)
                .allowedHeaders("Authorization", "Content-Type", "X-Session-Token", "X-Request-Id");
    }

    static List<String> parseAllowedOrigins(String origins) {
        return Arrays.stream(origins == null ? new String[0] : origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();
    }
}
