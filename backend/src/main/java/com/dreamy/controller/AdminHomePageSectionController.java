package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.site_builder.service.HomePageSectionService;
import com.dreamy.dto.SiteBuilderDtos.HomePageSectionDto;
import com.dreamy.dto.SiteBuilderDtos.HomePageSectionUpsert;
import com.dreamy.dto.SiteBuilderDtos.HomePageDraftSaveRequest;
import com.dreamy.dto.SiteBuilderDtos.SortRequest;
import com.dreamy.dto.SiteBuilderDtos.ToggleRequest;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台首页区块控制器（/api/admin/site-builder/home-sections）。
 * RBAC /site/home（KD-15 新建权限点）。
 */
@RestController
public class AdminHomePageSectionController {

    private static final String PERMISSION = "/site/home";

    private final HomePageSectionService homePageSectionService;

    public AdminHomePageSectionController(HomePageSectionService homePageSectionService) {
        this.homePageSectionService = homePageSectionService;
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/site-builder/home-sections")
    public ResponseEntity<R<Map<String, List<HomePageSectionDto>>>> list(
            @RequestParam(required = false) Boolean enabled_only) {
        return ResponseEntity.ok(R.ok(Map.of("items", homePageSectionService.list(enabled_only))));
    }

    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/site-builder/home-sections")
    public ResponseEntity<R<HomePageSectionDto>> create(@RequestBody HomePageSectionUpsert req) {
        return ResponseEntity.status(201).body(R.ok(homePageSectionService.create(req)));
    }

    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/site-builder/home-sections/draft")
    public ResponseEntity<R<Map<String, List<HomePageSectionDto>>>> saveDraft(
            @RequestBody HomePageDraftSaveRequest req) {
        return ResponseEntity.ok(R.ok(Map.of("items", homePageSectionService.saveDraft(req.getItems()))));
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/site-builder/home-sections/{id}")
    public ResponseEntity<R<HomePageSectionDto>> get(@PathVariable Long id) {
        return ResponseEntity.ok(R.ok(homePageSectionService.get(id)));
    }

    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/site-builder/home-sections/{id}")
    public ResponseEntity<R<HomePageSectionDto>> update(@PathVariable Long id,
                                                        @RequestBody HomePageSectionUpsert req) {
        return ResponseEntity.ok(R.ok(homePageSectionService.update(id, req)));
    }

    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/site-builder/home-sections/{id}")
    public ResponseEntity<R<Void>> delete(@PathVariable Long id) {
        homePageSectionService.delete(id);
        return ResponseEntity.ok(R.ok(null));
    }

    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/site-builder/home-sections/sort")
    public ResponseEntity<R<Void>> sort(@RequestBody SortRequest req) {
        homePageSectionService.batchSort(req.getItems());
        return ResponseEntity.ok(R.ok(null));
    }

    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/site-builder/home-sections/{id}/toggle")
    public ResponseEntity<R<HomePageSectionDto>> toggle(@PathVariable Long id,
                                                        @RequestBody ToggleRequest req) {
        return ResponseEntity.ok(R.ok(homePageSectionService.toggle(id, req.getEnabled())));
    }
}
