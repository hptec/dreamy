package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.product.service.FabricCareService;
import com.dreamy.dto.FabricCareDtos.*;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 护理标签字典 CRUD 控制器（E-FC-05~09；RBAC /products）。
 * L2 TRACE: catalog-fabric-care-api-detail §3。
 */
@RestController
public class ProductFabricCareController {

    private static final String PERMISSION = "/products";

    private final FabricCareService fabricCareService;

    public ProductFabricCareController(FabricCareService fabricCareService) {
        this.fabricCareService = fabricCareService;
    }

    /** E-FC-05 listAdminCareInstructions — GET /api/admin/care-instructions */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/care-instructions")
    public ResponseEntity<R<Map<String, List<CareInstructionDefDto>>>> list(
            @RequestParam(required = false) Integer category) {
        return ResponseEntity.ok(R.ok(Map.of("items", fabricCareService.listCareInstructions(category))));
    }

    /** E-FC-06 createAdminCareInstruction — POST /api/admin/care-instructions */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/care-instructions")
    public ResponseEntity<R<CareInstructionDefDto>> create(@RequestBody CareInstructionUpsert req) {
        return ResponseEntity.status(201).body(R.ok(fabricCareService.createCareInstruction(req)));
    }

    /** E-FC-07 updateAdminCareInstruction — PUT /api/admin/care-instructions/{id} */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/care-instructions/{id}")
    public ResponseEntity<R<CareInstructionDefDto>> update(@PathVariable String id,
                                                           @RequestBody CareInstructionUpsert req) {
        return ResponseEntity.ok(R.ok(fabricCareService.updateCareInstruction(parseId(id), req)));
    }

    /** E-FC-08 deleteAdminCareInstruction — DELETE /api/admin/care-instructions/{id} */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/care-instructions/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        fabricCareService.deleteCareInstruction(parseId(id));
        return ResponseEntity.noContent().build();
    }

    /** E-FC-09 toggleAdminCareInstructionStatus — PATCH /api/admin/care-instructions/{id}/status */
    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/care-instructions/{id}/status")
    public ResponseEntity<R<CareInstructionDefDto>> toggleStatus(@PathVariable String id,
                                                                  @RequestBody CareStatusToggle req) {
        return ResponseEntity.ok(R.ok(fabricCareService.toggleStatus(parseId(id), req.status())));
    }

    /** V-FC-017：id 非法视同不存在 → 422512 */
    private Long parseId(String raw) {
        try {
            long v = Long.parseLong(raw);
            if (v > 0) return v;
        } catch (NumberFormatException ignored) { }
        throw new CatalogException(CatalogErrorCode.CARE_NOT_FOUND);
    }
}
