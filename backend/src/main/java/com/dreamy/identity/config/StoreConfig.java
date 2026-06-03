package com.dreamy.identity.config;

import com.dreamy.identity.security.StoreJwtFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Store CORS 配置（origin http://localhost:5173）+ Filter 注册。
 * 约束: shared-contracts cors（portal-store 5173，credentials=true，allowed_headers）。
 */
@Configuration
public class StoreConfig {

    @Bean
    public CorsFilter storeCorsFilter() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("http://localhost:5173"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/store/**", cfg);
        return new CorsFilter(source);
    }

    @Bean
    public FilterRegistrationBean<StoreJwtFilter> storeJwtFilterRegistration(StoreJwtFilter filter) {
        FilterRegistrationBean<StoreJwtFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/store/*");
        reg.setOrder(10);
        return reg;
    }
}
