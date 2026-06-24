package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.site_builder.service.FooterService;
import com.dreamy.domain.site_builder.service.NavigationService;
import com.dreamy.dto.SiteBuilderDtos.FooterColumnDto;
import com.dreamy.dto.SiteBuilderDtos.FooterSaveRequest;
import com.dreamy.dto.SiteBuilderDtos.NavigationItemDto;
import com.dreamy.dto.SiteBuilderDtos.NavigationSaveRequest;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AdminNavigationController {

    private static final String PERMISSION = "/site/navigation";

    private final NavigationService navigationService;
    private final FooterService footerService;

    public AdminNavigationController(NavigationService navigationService, FooterService footerService) {
        this.navigationService = navigationService;
        this.footerService = footerService;
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/site-builder/navigation")
    public ResponseEntity<R<Map<String, List<NavigationItemDto>>>> getNavigation() {
        return ResponseEntity.ok(R.ok(Map.of("items", navigationService.list())));
    }

    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/site-builder/navigation")
    public ResponseEntity<R<Map<String, List<NavigationItemDto>>>> saveNavigation(
            @RequestBody NavigationSaveRequest req) {
        return ResponseEntity.ok(R.ok(Map.of("items", navigationService.save(req))));
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/site-builder/footer")
    public ResponseEntity<R<Map<String, List<FooterColumnDto>>>> getFooter() {
        return ResponseEntity.ok(R.ok(Map.of("columns", footerService.list())));
    }

    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/site-builder/footer")
    public ResponseEntity<R<Map<String, List<FooterColumnDto>>>> saveFooter(
            @RequestBody FooterSaveRequest req) {
        return ResponseEntity.ok(R.ok(Map.of("columns", footerService.save(req))));
    }
}
