package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.guide.service.GuideService;
import com.dreamy.dto.AdminMarketingDtos.GuideDto;
import com.dreamy.dto.AdminMarketingDtos.GuideUpsert;
import com.dreamy.dto.AdminMarketingDtos.StatusPatch;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
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
 * 后台指南控制器（E-MKT-42~46；RBAC `/content/lookbook`——契约口径：与 Lookbook 同页同权限；不缓存）。
 */
@RestController
public class AdminGuideController {

    private static final String PERMISSION = "/content/lookbook";

    private final GuideService guideService;

    public AdminGuideController(GuideService guideService) {
        this.guideService = guideService;
    }

    /** E-MKT-42 listAdminGuides */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/content/guides")
    public ResponseEntity<R<Map<String, List<GuideDto>>>> list(@RequestParam(required = false) Integer status) {
        return ResponseEntity.ok(R.ok(Map.of("items", guideService.listAdmin(status))));
    }

    /** E-MKT-43 createAdminGuide（TX-MKT-023） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/content/guides")
    public ResponseEntity<R<GuideDto>> create(@RequestBody GuideUpsert req) {
        return ResponseEntity.status(201).body(R.ok(guideService.create(req)));
    }

    /** E-MKT-44 updateAdminGuide（TX-MKT-024） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/content/guides/{id}")
    public ResponseEntity<R<GuideDto>> update(@PathVariable String id, @RequestBody GuideUpsert req) {
        return ResponseEntity.ok(R.ok(guideService.update(parseId(id), req)));
    }

    /** E-MKT-45 deleteAdminGuide（TX-MKT-025） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/content/guides/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        guideService.delete(parseId(id));
        return ResponseEntity.noContent().build();
    }

    /** E-MKT-46 patchAdminGuideStatus（TX-MKT-026） */
    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/content/guides/{id}/status")
    public ResponseEntity<R<GuideDto>> patchStatus(@PathVariable String id, @RequestBody StatusPatch req) {
        return ResponseEntity.ok(R.ok(guideService.patchStatus(parseId(id), req.status())));
    }

    /** V-MKT-081：id 非法视同不存在 → 404701 */
    private Long parseId(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
    }
}
