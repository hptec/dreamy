package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.wedding.service.AdminWeddingService;
import com.dreamy.dto.AdminMarketingDtos.RealWeddingDto;
import com.dreamy.dto.AdminMarketingDtos.RealWeddingUpsert;
import com.dreamy.dto.AdminMarketingDtos.StatusPatch;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import huihao.page.Paginated;
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

/**
 * 后台婚礼案例控制器（E-MKT-32~36；RBAC `/content/weddings`；不缓存）。
 */
@RestController
public class AdminWeddingController {

    private static final String PERMISSION = "/content/weddings";

    private final AdminWeddingService adminWeddingService;

    public AdminWeddingController(AdminWeddingService adminWeddingService) {
        this.adminWeddingService = adminWeddingService;
    }

    /** E-MKT-32 listAdminWeddings */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/content/weddings")
    public ResponseEntity<R<Paginated<RealWeddingDto>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) Integer status) {
        return ResponseEntity.ok(R.ok(adminWeddingService.page(page, pageSize, status)));
    }

    /** E-MKT-33 createAdminWedding（TX-MKT-015） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/content/weddings")
    public ResponseEntity<R<RealWeddingDto>> create(@RequestBody RealWeddingUpsert req) {
        return ResponseEntity.status(201).body(R.ok(adminWeddingService.create(req)));
    }

    /** E-MKT-34 updateAdminWedding（TX-MKT-016） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/content/weddings/{id}")
    public ResponseEntity<R<RealWeddingDto>> update(@PathVariable String id, @RequestBody RealWeddingUpsert req) {
        return ResponseEntity.ok(R.ok(adminWeddingService.update(parseId(id), req)));
    }

    /** E-MKT-35 deleteAdminWedding（TX-MKT-017） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/content/weddings/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        adminWeddingService.delete(parseId(id));
        return ResponseEntity.noContent().build();
    }

    /** E-MKT-36 patchAdminWeddingStatus（TX-MKT-018） */
    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/content/weddings/{id}/status")
    public ResponseEntity<R<RealWeddingDto>> patchStatus(@PathVariable String id, @RequestBody StatusPatch req) {
        return ResponseEntity.ok(R.ok(adminWeddingService.patchStatus(parseId(id), req.status())));
    }

    /** V-MKT-064：id 非法视同不存在 → 404701 */
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
