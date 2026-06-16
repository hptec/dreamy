package com.dreamy.controller;

import com.dreamy.domain.attribute.service.AttributeDefService;
import com.dreamy.domain.attribute.service.AttributeSetService;
import com.dreamy.dto.AdminCatalogDtos.AttributeDefDto;
import com.dreamy.dto.AdminCatalogDtos.AttributeDefUpsert;
import com.dreamy.dto.AdminCatalogDtos.AttributeSetDto;
import com.dreamy.dto.AdminCatalogDtos.AttributeSetUpsert;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台属性集与属性字典控制器（E-CAT-19~26；RBAC `/attribute-sets`；不缓存）。
 */
@RestController
public class AdminAttributeController {

    private static final String PERMISSION = "/attribute-sets";

    private final AttributeSetService attributeSetService;
    private final AttributeDefService attributeDefService;

    public AdminAttributeController(AttributeSetService attributeSetService,
                                    AttributeDefService attributeDefService) {
        this.attributeSetService = attributeSetService;
        this.attributeDefService = attributeDefService;
    }

    // ==================== 属性集 E-CAT-19~22 ====================

    /** E-CAT-19 listAdminAttributeSets */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/attribute-sets")
    public ResponseEntity<R<Map<String, List<AttributeSetDto>>>> listSets() {
        return ResponseEntity.ok(R.ok(Map.of("items", attributeSetService.list())));
    }

    /** E-CAT-20 createAdminAttributeSet（TX-CAT-009） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/attribute-sets")
    public ResponseEntity<R<AttributeSetDto>> createSet(@RequestBody AttributeSetUpsert req) {
        return ResponseEntity.status(201).body(R.ok(attributeSetService.create(req)));
    }

    /** E-CAT-21 updateAdminAttributeSet（TX-CAT-010 三态矩阵整单覆盖） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/attribute-sets/{id}")
    public ResponseEntity<R<AttributeSetDto>> updateSet(@PathVariable String id,
                                                        @RequestBody AttributeSetUpsert req) {
        return ResponseEntity.ok(R.ok(attributeSetService.update(parseSetId(id), req)));
    }

    /** E-CAT-22 deleteAdminAttributeSet（TX-CAT-011；409503 guard） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/attribute-sets/{id}")
    public ResponseEntity<Void> deleteSet(@PathVariable String id) {
        attributeSetService.delete(parseSetId(id));
        return ResponseEntity.noContent().build();
    }

    // ==================== 属性字典 E-CAT-23~26 ====================

    /** E-CAT-23 listAdminAttributeDefs */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/attribute-defs")
    public ResponseEntity<R<Map<String, List<AttributeDefDto>>>> listDefs() {
        return ResponseEntity.ok(R.ok(Map.of("items", attributeDefService.list())));
    }

    /** E-CAT-24 createAdminAttributeDef（TX-CAT-012） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/attribute-defs")
    public ResponseEntity<R<AttributeDefDto>> createDef(@RequestBody AttributeDefUpsert req) {
        return ResponseEntity.status(201).body(R.ok(attributeDefService.create(req)));
    }

    /** E-CAT-25 updateAdminAttributeDef（TX-CAT-013） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/attribute-defs/{id}")
    public ResponseEntity<R<AttributeDefDto>> updateDef(@PathVariable String id,
                                                        @RequestBody AttributeDefUpsert req) {
        return ResponseEntity.ok(R.ok(attributeDefService.update(parseDefId(id), req)));
    }

    /** E-CAT-26 deleteAdminAttributeDef（TX-CAT-014；409507 guard；force=true 级联删除） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/attribute-defs/{id}")
    public ResponseEntity<Void> deleteDef(@PathVariable String id,
                                          @RequestParam(defaultValue = "false") boolean force) {
        attributeDefService.delete(parseDefId(id), force);
        return ResponseEntity.noContent().build();
    }

    /** id 非法视同不存在 → 404503 */
    private Long parseSetId(String raw) {
        Long id = tryParse(raw);
        if (id == null) {
            throw new CatalogException(CatalogErrorCode.ATTRIBUTE_SET_NOT_FOUND);
        }
        return id;
    }

    /** id 非法视同不存在 → 404504 */
    private Long parseDefId(String raw) {
        Long id = tryParse(raw);
        if (id == null) {
            throw new CatalogException(CatalogErrorCode.ATTRIBUTE_DEF_NOT_FOUND);
        }
        return id;
    }

    private Long tryParse(String raw) {
        try {
            long value = Long.parseLong(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
