package com.dreamy.catalog.controller;

import com.dreamy.catalog.domain.tag.service.TagAdminService;
import com.dreamy.catalog.dto.AdminCatalogDtos.TagDimensionDto;
import com.dreamy.catalog.dto.AdminCatalogDtos.TagDimensionUpsert;
import com.dreamy.catalog.dto.AdminCatalogDtos.TagDto;
import com.dreamy.catalog.dto.AdminCatalogDtos.TagUpsert;
import com.dreamy.catalog.error.CatalogErrorCode;
import com.dreamy.catalog.error.CatalogException;
import com.dreamy.identity.aspect.RequirePermission;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台标签维度与标签控制器（E-CAT-27~34；RBAC `/categories`；不缓存）。
 */
@RestController
public class AdminTagController {

    private static final String PERMISSION = "/categories";

    private final TagAdminService tagAdminService;

    public AdminTagController(TagAdminService tagAdminService) {
        this.tagAdminService = tagAdminService;
    }

    // ==================== 标签维度 E-CAT-27~30 ====================

    /** E-CAT-27 listAdminTagDimensions */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/tag-dimensions")
    public ResponseEntity<R<Map<String, List<TagDimensionDto>>>> listDimensions() {
        return ResponseEntity.ok(R.ok(Map.of("items", tagAdminService.listDimensions())));
    }

    /** E-CAT-28 createAdminTagDimension（TX-CAT-015） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/tag-dimensions")
    public ResponseEntity<R<TagDimensionDto>> createDimension(@RequestBody TagDimensionUpsert req) {
        return ResponseEntity.status(201).body(R.ok(tagAdminService.createDimension(req)));
    }

    /** E-CAT-29 updateAdminTagDimension（TX-CAT-016） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/tag-dimensions/{id}")
    public ResponseEntity<R<TagDimensionDto>> updateDimension(@PathVariable String id,
                                                              @RequestBody TagDimensionUpsert req) {
        return ResponseEntity.ok(R.ok(tagAdminService.updateDimension(parseId(id), req)));
    }

    /** E-CAT-30 deleteAdminTagDimension（TX-CAT-017；409506 guard） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/tag-dimensions/{id}")
    public ResponseEntity<Void> deleteDimension(@PathVariable String id) {
        tagAdminService.deleteDimension(parseId(id));
        return ResponseEntity.noContent().build();
    }

    // ==================== 标签 E-CAT-31~34 ====================

    /** E-CAT-31 listAdminTags（V-CAT-062 dimension_id 可选） */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/tags")
    public ResponseEntity<R<Map<String, List<TagDto>>>> listTags(
            @RequestParam(name = "dimension_id", required = false) Long dimensionId) {
        return ResponseEntity.ok(R.ok(Map.of("items", tagAdminService.listTags(dimensionId))));
    }

    /** E-CAT-32 createAdminTag（TX-CAT-018） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/tags")
    public ResponseEntity<R<TagDto>> createTag(@RequestBody TagUpsert req) {
        return ResponseEntity.status(201).body(R.ok(tagAdminService.createTag(req)));
    }

    /** E-CAT-33 updateAdminTag（TX-CAT-019） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/tags/{id}")
    public ResponseEntity<R<TagDto>> updateTag(@PathVariable String id, @RequestBody TagUpsert req) {
        return ResponseEntity.ok(R.ok(tagAdminService.updateTag(parseId(id), req)));
    }

    /** E-CAT-34 deleteAdminTag（TX-CAT-020；product_tag 级联摘除） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/tags/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable String id) {
        tagAdminService.deleteTag(parseId(id));
        return ResponseEntity.noContent().build();
    }

    /** V-CAT-068：id 非法视同不存在 → 404505 */
    private Long parseId(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new CatalogException(CatalogErrorCode.TAG_NOT_FOUND);
    }
}
