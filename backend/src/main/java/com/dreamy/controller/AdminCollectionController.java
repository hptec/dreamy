package com.dreamy.controller;

import com.dreamy.domain.collection.service.CollectionAdminService;
import com.dreamy.dto.AdminCatalogDtos.CollectionGroupDto;
import com.dreamy.dto.AdminCatalogDtos.CollectionGroupUpsert;
import com.dreamy.dto.AdminCatalogDtos.CollectionDto;
import com.dreamy.dto.AdminCatalogDtos.CollectionUpsert;
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
 * 后台集合分组与集合控制器（E-CAT-27~34；RBAC `/categories`；不缓存）。
 */
@RestController
public class AdminCollectionController {

    private static final String PERMISSION = "/categories";

    private final CollectionAdminService collectionAdminService;

    public AdminCollectionController(CollectionAdminService collectionAdminService) {
        this.collectionAdminService = collectionAdminService;
    }

    // ==================== 集合分组 E-CAT-27~30 ====================

    /** E-CAT-27 listAdminCollectionGroups */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/collection-groups")
    public ResponseEntity<R<Map<String, List<CollectionGroupDto>>>> listGroups() {
        return ResponseEntity.ok(R.ok(Map.of("items", collectionAdminService.listGroups())));
    }

    /** E-CAT-28 createAdminCollectionGroup（TX-CAT-015） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/collection-groups")
    public ResponseEntity<R<CollectionGroupDto>> createGroup(@RequestBody CollectionGroupUpsert req) {
        return ResponseEntity.status(201).body(R.ok(collectionAdminService.createGroup(req)));
    }

    /** E-CAT-29 updateAdminCollectionGroup（TX-CAT-016） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/collection-groups/{id}")
    public ResponseEntity<R<CollectionGroupDto>> updateGroup(@PathVariable String id,
                                                             @RequestBody CollectionGroupUpsert req) {
        return ResponseEntity.ok(R.ok(collectionAdminService.updateGroup(parseId(id), req)));
    }

    /** E-CAT-30 deleteAdminCollectionGroup（TX-CAT-017；409506 guard） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/collection-groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id) {
        collectionAdminService.deleteGroup(parseId(id));
        return ResponseEntity.noContent().build();
    }

    // ==================== 集合 E-CAT-31~34 ====================

    /** E-CAT-31 listAdminCollections（V-CAT-062 group_id 可选） */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/collections")
    public ResponseEntity<R<Map<String, List<CollectionDto>>>> listCollections(
            @RequestParam(name = "group_id", required = false) Long groupId) {
        return ResponseEntity.ok(R.ok(Map.of("items", collectionAdminService.listCollections(groupId))));
    }

    /** E-CAT-32 createAdminCollection（TX-CAT-018） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/collections")
    public ResponseEntity<R<CollectionDto>> createCollection(@RequestBody CollectionUpsert req) {
        return ResponseEntity.status(201).body(R.ok(collectionAdminService.createCollection(req)));
    }

    /** E-CAT-33 updateAdminCollection（TX-CAT-019） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/collections/{id}")
    public ResponseEntity<R<CollectionDto>> updateCollection(@PathVariable String id, @RequestBody CollectionUpsert req) {
        return ResponseEntity.ok(R.ok(collectionAdminService.updateCollection(parseId(id), req)));
    }

    /** E-CAT-34 deleteAdminCollection（TX-CAT-020；product_collection 级联摘除） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/collections/{id}")
    public ResponseEntity<Void> deleteCollection(@PathVariable String id) {
        collectionAdminService.deleteCollection(parseId(id));
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
        throw new CatalogException(CatalogErrorCode.COLLECTION_NOT_FOUND);
    }
}
