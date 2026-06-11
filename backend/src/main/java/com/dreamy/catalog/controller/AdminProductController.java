package com.dreamy.catalog.controller;

import com.dreamy.catalog.domain.product.service.AdminProductBatchService;
import com.dreamy.catalog.domain.product.service.AdminProductExportService;
import com.dreamy.catalog.domain.product.service.AdminProductExportService.ExportResult;
import com.dreamy.catalog.domain.product.service.AdminProductService;
import com.dreamy.catalog.dto.AdminProductBatchDtos.BatchRequest;
import com.dreamy.catalog.dto.AdminProductBatchDtos.BatchResult;
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
 * admin-prototype-alignment 增量：API-CAT-01 批量操作 / API-CAT-02 CSV 导出。
 * 审计在 Service 事务内写入（changes before/after），不挂 identity @AuditLog 切面避免重复记账。
 */
@RestController
public class AdminProductController {

    private static final String PERMISSION = "/products";

    private final AdminProductService adminProductService;
    private final AdminProductBatchService batchService;
    private final AdminProductExportService exportService;

    public AdminProductController(AdminProductService adminProductService,
                                  AdminProductBatchService batchService,
                                  AdminProductExportService exportService) {
        this.adminProductService = adminProductService;
        this.batchService = batchService;
        this.exportService = exportService;
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

    /**
     * API-CAT-01 batchAdminProducts（admin-prototype-alignment）。V-003 鉴权 + 权限点 /products；
     * 逐条容错：部分/全部失败仍 200，由调用方按 failures 展示。
     */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/products/batch")
    public ResponseEntity<R<BatchResult>> batch(@RequestBody BatchRequest req) {
        return ResponseEntity.ok(R.ok(batchService.execute(req.action(), req.ids())));
    }

    /**
     * API-CAT-02 exportAdminProducts（admin-prototype-alignment）。
     * 出参 200 text/csv; charset=UTF-8（带 BOM）；Content-Disposition attachment filename="products-{yyyyMMdd}.csv"；
     * 截断时响应头 X-Export-Truncated: true（STEP-03）。
     */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/products/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String status,
                                         @RequestParam(name = "category_id", required = false) Long categoryId,
                                         @RequestParam(required = false) String search) {
        ExportResult result = exportService.export(status, categoryId, search);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"" + result.fileName() + "\"");
        if (result.truncated()) {
            builder.header("X-Export-Truncated", "true");
        }
        return builder.body(result.content());
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
