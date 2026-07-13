package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.site_builder.service.HomePagePublicationService;
import com.dreamy.dto.SiteBuilderDtos.HomePagePreviewTokenDto;
import com.dreamy.dto.SiteBuilderDtos.HomePagePublicationStatusDto;
import com.dreamy.dto.SiteBuilderDtos.HomePagePublishRequest;
import com.dreamy.dto.SiteBuilderDtos.HomePageReleaseDto;
import com.dreamy.dto.SiteBuilderDtos.StoreHomePageDto;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AdminHomePagePublicationController {

    private static final String PERMISSION = "/site/home";
    private final HomePagePublicationService publicationService;

    public AdminHomePagePublicationController(HomePagePublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/site-builder/home-publication/status")
    public ResponseEntity<R<HomePagePublicationStatusDto>> status() {
        return ResponseEntity.ok(R.ok(publicationService.status()));
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/site-builder/home-publication/history")
    public ResponseEntity<R<Map<String, List<HomePageReleaseDto>>>> history(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(R.ok(Map.of("items", publicationService.history(limit))));
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/site-builder/home-publication/preview")
    public ResponseEntity<R<StoreHomePageDto>> preview(@RequestParam(defaultValue = "en") String locale) {
        return ResponseEntity.ok(R.ok(publicationService.previewDraft(locale)));
    }

    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/site-builder/home-publication/preview-token")
    public ResponseEntity<R<HomePagePreviewTokenDto>> previewToken() {
        return ResponseEntity.status(201).body(R.ok(publicationService.createPreviewToken()));
    }

    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/site-builder/home-publication/publish")
    public ResponseEntity<R<HomePageReleaseDto>> publish(@RequestBody(required = false) HomePagePublishRequest req) {
        return ResponseEntity.ok(R.ok(publicationService.publish(
                req == null ? null : req.getName(),
                req == null ? null : req.getExpectedDraftRevision())));
    }

    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/site-builder/home-publication/releases/{id}/rollback")
    public ResponseEntity<R<HomePageReleaseDto>> rollback(@PathVariable Long id) {
        return ResponseEntity.ok(R.ok(publicationService.rollback(id)));
    }
}
