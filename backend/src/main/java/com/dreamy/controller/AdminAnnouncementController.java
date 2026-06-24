package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.site_builder.entity.Announcement;
import com.dreamy.domain.site_builder.service.AnnouncementService;
import com.dreamy.dto.SiteBuilderDtos.AnnouncementDto;
import com.dreamy.dto.SiteBuilderDtos.AnnouncementUpsert;
import com.dreamy.dto.SiteBuilderDtos.ToggleRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;
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
import java.util.stream.Collectors;

@RestController
public class AdminAnnouncementController {

    private static final String PERMISSION = "/site/announcement";

    private final AnnouncementService announcementService;

    public AdminAnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/site-builder/announcements")
    public ResponseEntity<R<Map<String, Object>>> list(
            @RequestParam(required = false) Boolean enabled_only,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size) {
        IPage<Announcement> p = announcementService.list(page, page_size, enabled_only);
        List<AnnouncementDto> items = p.getRecords().stream()
                .map(a -> announcementService.get(a.getId()))
                .collect(Collectors.toList());
        Map<String, Object> paginated = Map.of(
                "data", items,
                "total_elements", p.getTotal(),
                "page_number", p.getCurrent(),
                "page_size", p.getSize(),
                "number_of_elements", p.getRecords().size(),
                "total_pages", p.getPages());
        return ResponseEntity.ok(R.ok(paginated));
    }

    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/site-builder/announcements")
    public ResponseEntity<R<AnnouncementDto>> create(@RequestBody AnnouncementUpsert req) {
        return ResponseEntity.status(201).body(R.ok(announcementService.create(req)));
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/site-builder/announcements/{id}")
    public ResponseEntity<R<AnnouncementDto>> get(@PathVariable Long id) {
        return ResponseEntity.ok(R.ok(announcementService.get(id)));
    }

    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/site-builder/announcements/{id}")
    public ResponseEntity<R<AnnouncementDto>> update(@PathVariable Long id,
                                                    @RequestBody AnnouncementUpsert req) {
        return ResponseEntity.ok(R.ok(announcementService.update(id, req)));
    }

    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/site-builder/announcements/{id}")
    public ResponseEntity<R<Void>> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return ResponseEntity.ok(R.ok(null));
    }

    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/site-builder/announcements/{id}/toggle")
    public ResponseEntity<R<AnnouncementDto>> toggle(@PathVariable Long id,
                                                     @RequestBody ToggleRequest req) {
        return ResponseEntity.ok(R.ok(announcementService.toggle(id, req.getEnabled())));
    }
}
