package com.dreamy.catalog.controller;

import com.dreamy.catalog.dto.PresignDtos.PresignRequest;
import com.dreamy.catalog.dto.PresignDtos.PresignResponse;
import com.dreamy.catalog.infra.CatalogPresignService;
import com.dreamy.identity.aspect.RequirePermission;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台预签名上传控制器（E-CAT-35；RBAC `/products`；媒体基建由 catalog 域代管——决策 9）。
 */
@RestController
public class AdminUploadController {

    private final CatalogPresignService catalogPresignService;

    public AdminUploadController(CatalogPresignService catalogPresignService) {
        this.catalogPresignService = catalogPresignService;
    }

    /** E-CAT-35 presignAdminUpload */
    @RequirePermission("/products")
    @PostMapping("/api/admin/uploads/presign")
    public ResponseEntity<R<PresignResponse>> presign(@RequestBody PresignRequest req) {
        return ResponseEntity.ok(R.ok(catalogPresignService.presign(req)));
    }
}
