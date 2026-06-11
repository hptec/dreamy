package com.dreamy.catalog.controller;

import com.dreamy.catalog.domain.product.service.AdminProductService;
import com.dreamy.catalog.dto.AdminProductDetail;
import com.dreamy.catalog.dto.AdminProductListItem;
import com.dreamy.catalog.dto.AdminProductUpsert;
import com.dreamy.catalog.error.CatalogErrorCode;
import com.dreamy.catalog.error.CatalogException;
import com.dreamy.identity.aspect.RequirePermission;
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

import java.util.Map;

/**
 * 后台商品控制器（E-CAT-08~14；AdminBearerAuth + RBAC `/products`；不缓存）。
 * 审计在 Service 事务内写入（changes before/after），不挂 identity @AuditLog 切面避免重复记账。
 */
@RestController
public class AdminProductController {

    private static final String PERMISSION = "/products";

    private final AdminProductService adminProductService;

    public AdminProductController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    /** E-CAT-08 listAdminProducts */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/products")
    public ResponseEntity<R<Paginated<AdminProductListItem>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(R.ok(adminProductService.pageList(page, pageSize, status, categoryId, search)));
    }

    /** E-CAT-09 createAdminProduct（TX-CAT-001） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/products")
    public ResponseEntity<R<AdminProductDetail>> create(@RequestBody AdminProductUpsert req) {
        return ResponseEntity.status(201).body(R.ok(adminProductService.create(req)));
    }

    /** E-CAT-10 getAdminProduct */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/products/{id}")
    public ResponseEntity<R<AdminProductDetail>> get(@PathVariable String id) {
        return ResponseEntity.ok(R.ok(adminProductService.get(parseId(id))));
    }

    /** E-CAT-11 updateAdminProduct（TX-CAT-002，保存并生成静态页语义） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/products/{id}")
    public ResponseEntity<R<AdminProductDetail>> update(@PathVariable String id,
                                                        @RequestBody AdminProductUpsert req) {
        return ResponseEntity.ok(R.ok(adminProductService.update(parseId(id), req)));
    }

    /** E-CAT-12 deleteAdminProduct（TX-CAT-003；published → 409509） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/products/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        adminProductService.delete(parseId(id));
        return ResponseEntity.noContent().build();
    }

    /** E-CAT-13 toggleAdminProductStatus（TX-CAT-004） */
    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/products/{id}/status")
    public ResponseEntity<R<AdminProductListItem>> toggleStatus(@PathVariable String id,
                                                                @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(R.ok(adminProductService.toggleStatus(parseId(id), body.get("status"))));
    }

    /** E-CAT-14 patchAdminProductFlags（TX-CAT-005） */
    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/products/{id}/flags")
    public ResponseEntity<R<AdminProductListItem>> patchFlags(@PathVariable String id,
                                                              @RequestBody FlagsPatch body) {
        return ResponseEntity.ok(R.ok(adminProductService.patchFlags(parseId(id),
                body.isNew(), body.isBest(), body.recommend(), body.sort())));
    }

    /** flags 局部载荷（minProperties=1 校验在 Service——V-CAT-041） */
    public record FlagsPatch(Boolean isNew, Boolean isBest, Boolean recommend, Integer sort) {
    }

    /** V-CAT-037/039：id 非法视同不存在 → 404501 同口径 */
    private Long parseId(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
    }
}
