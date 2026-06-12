package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.banner.service.AdminBannerService;
import com.dreamy.dto.AdminMarketingDtos.BannerDto;
import com.dreamy.dto.AdminMarketingDtos.BannerUpsert;
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
 * 后台 Banner 控制器（E-MKT-21~25；RBAC `/banners`；不缓存）。
 */
@RestController
public class AdminBannerController {

    private static final String PERMISSION = "/banners";

    private final AdminBannerService adminBannerService;

    public AdminBannerController(AdminBannerService adminBannerService) {
        this.adminBannerService = adminBannerService;
    }

    /** E-MKT-21 listAdminBanners */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/banners")
    public ResponseEntity<R<Map<String, List<BannerDto>>>> list(@RequestParam(required = false) Integer position) {
        return ResponseEntity.ok(R.ok(Map.of("items", adminBannerService.list(position))));
    }

    /** E-MKT-22 createAdminBanner（TX-MKT-007） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/banners")
    public ResponseEntity<R<BannerDto>> create(@RequestBody BannerUpsert req) {
        return ResponseEntity.status(201).body(R.ok(adminBannerService.create(req)));
    }

    /** E-MKT-23 updateAdminBanner（TX-MKT-008） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/banners/{id}")
    public ResponseEntity<R<BannerDto>> update(@PathVariable String id, @RequestBody BannerUpsert req) {
        return ResponseEntity.ok(R.ok(adminBannerService.update(parseId(id), req)));
    }

    /** E-MKT-24 deleteAdminBanner（TX-MKT-009） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/banners/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        adminBannerService.delete(parseId(id));
        return ResponseEntity.noContent().build();
    }

    /** E-MKT-25 toggleAdminBannerStatus（TX-MKT-010；行内 Toggle） */
    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/banners/{id}/status")
    public ResponseEntity<R<BannerDto>> toggleStatus(@PathVariable String id, @RequestBody StatusPatch req) {
        return ResponseEntity.ok(R.ok(adminBannerService.toggleStatus(parseId(id), req.status())));
    }

    /** V-MKT-045：id 非法视同不存在 → 404701 */
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
