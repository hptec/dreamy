package com.dreamy.config;

import com.dreamy.security.StoreJwtFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Store CORS + Filter 注册。
 * 约束: shared-contracts cors（portal-store 5173，credentials=true，allowed_headers）。
 * origin 由 STORE_CORS_ORIGIN 注入（逗号分隔多值），dev 默认 http://localhost:5173。
 */
@Configuration
public class StoreConfig {

    private static final String DEFAULT_STORE_CORS_ORIGIN = "http://localhost:5173";

    @Bean
    public CorsFilter storeCorsFilter() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(splitOrigins(System.getenv("STORE_CORS_ORIGIN"), DEFAULT_STORE_CORS_ORIGIN));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/store/**", cfg);
        return new CorsFilter(source);
    }

    static List<String> splitOrigins(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return List.of(fallback);
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> storeCorsFilterRegistration(CorsFilter storeCorsFilter) {
        FilterRegistrationBean<CorsFilter> reg = new FilterRegistrationBean<>(storeCorsFilter);
        reg.addUrlPatterns("/api/store/*");
        reg.setOrder(0);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<StoreJwtFilter> storeJwtFilterRegistration(StoreJwtFilter filter) {
        FilterRegistrationBean<StoreJwtFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/store/*");
        reg.setOrder(10);
        return reg;
    }
}
