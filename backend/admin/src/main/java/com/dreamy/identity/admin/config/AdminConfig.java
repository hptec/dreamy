package com.dreamy.identity.admin.config;

import com.dreamy.identity.admin.filter.AdminJwtFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Admin CORS（origin http://localhost:5174）+ Filter 注册。
 * 约束: shared-contracts cors（portal-admin 5174，credentials=true）。
 */
@Configuration
public class AdminConfig {

    @Bean
    public CorsFilter adminCorsFilter() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("http://localhost:5174"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/admin/**", cfg);
        return new CorsFilter(source);
    }

    @Bean
    public FilterRegistrationBean<AdminJwtFilter> adminJwtFilterRegistration(AdminJwtFilter filter) {
        FilterRegistrationBean<AdminJwtFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/admin/*");
        reg.setOrder(20);
        return reg;
    }
}
