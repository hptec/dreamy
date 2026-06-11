package com.dreamy.marketing.controller;

import com.dreamy.identity.aspect.RequirePermission;
import com.dreamy.marketing.domain.lookbook.service.AdminLookbookService;
import com.dreamy.marketing.dto.AdminMarketingDtos.LookbookDto;
import com.dreamy.marketing.dto.AdminMarketingDtos.LookbookUpsert;
import com.dreamy.marketing.dto.AdminMarketingDtos.StatusPatch;
import com.dreamy.marketing.error.MarketingErrorCode;
import com.dreamy.marketing.error.MarketingException;
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
 * 后台 Lookbook 控制器（E-MKT-37~41；RBAC `/content/lookbook`；不缓存）。
 */
@RestController
public class AdminLookbookController {

    private static final String PERMISSION = "/content/lookbook";

    private final AdminLookbookService adminLookbookService;

    public AdminLookbookController(AdminLookbookService adminLookbookService) {
        this.adminLookbookService = adminLookbookService;
    }

    /** E-MKT-37 listAdminLookbooks */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/content/lookbooks")
    public ResponseEntity<R<Map<String, List<LookbookDto>>>> list(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(R.ok(Map.of("items", adminLookbookService.list(status))));
    }

    /** E-MKT-38 createAdminLookbook（TX-MKT-019） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/content/lookbooks")
    public ResponseEntity<R<LookbookDto>> create(@RequestBody LookbookUpsert req) {
        return ResponseEntity.status(201).body(R.ok(adminLookbookService.create(req)));
    }

    /** E-MKT-39 updateAdminLookbook（TX-MKT-020） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/content/lookbooks/{id}")
    public ResponseEntity<R<LookbookDto>> update(@PathVariable String id, @RequestBody LookbookUpsert req) {
        return ResponseEntity.ok(R.ok(adminLookbookService.update(parseId(id), req)));
    }

    /** E-MKT-40 deleteAdminLookbook（TX-MKT-021） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/content/lookbooks/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        adminLookbookService.delete(parseId(id));
        return ResponseEntity.noContent().build();
    }

    /** E-MKT-41 patchAdminLookbookStatus（TX-MKT-022） */
    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/content/lookbooks/{id}/status")
    public ResponseEntity<R<LookbookDto>> patchStatus(@PathVariable String id, @RequestBody StatusPatch req) {
        return ResponseEntity.ok(R.ok(adminLookbookService.patchStatus(parseId(id), req.status())));
    }

    /** V-MKT-072：id 非法视同不存在 → 404701 */
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
