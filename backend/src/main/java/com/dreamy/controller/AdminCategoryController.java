package com.dreamy.controller;

import com.dreamy.domain.category.service.AdminCategoryService;
import com.dreamy.dto.AdminCategoryNode;
import com.dreamy.dto.AdminCategoryUpsert;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.aspect.RequirePermission;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台分类控制器（E-CAT-15~18；RBAC `/categories`；不缓存）。
 */
@RestController
public class AdminCategoryController {

    private static final String PERMISSION = "/categories";

    private final AdminCategoryService adminCategoryService;

    public AdminCategoryController(AdminCategoryService adminCategoryService) {
        this.adminCategoryService = adminCategoryService;
    }

    /** E-CAT-15 listAdminCategories */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/categories")
    public ResponseEntity<R<Map<String, List<AdminCategoryNode>>>> list() {
        return ResponseEntity.ok(R.ok(Map.of("items", adminCategoryService.listTree())));
    }

    /** E-CAT-16 createAdminCategory（TX-CAT-006） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/categories")
    public ResponseEntity<R<AdminCategoryNode>> create(@RequestBody AdminCategoryUpsert req) {
        return ResponseEntity.status(201).body(R.ok(adminCategoryService.create(req)));
    }

    /** E-CAT-17 updateAdminCategory（TX-CAT-007） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/categories/{id}")
    public ResponseEntity<R<AdminCategoryNode>> update(@PathVariable String id,
                                                       @RequestBody AdminCategoryUpsert req) {
        return ResponseEntity.ok(R.ok(adminCategoryService.update(parseId(id), req)));
    }

    /** E-CAT-18 deleteAdminCategory（TX-CAT-008；409502 guard） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/categories/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        adminCategoryService.delete(parseId(id));
        return ResponseEntity.noContent().build();
    }

    /** id 非法视同不存在 → 404502（V-CAT-037 口径） */
    private Long parseId(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new CatalogException(CatalogErrorCode.CATEGORY_NOT_FOUND);
    }
}
