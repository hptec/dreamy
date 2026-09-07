package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.shippingrate.service.ShippingOptionAdminService;
import com.dreamy.domain.shippingrate.service.ShippingQuotePreviewService;
import com.dreamy.dto.TradingDtos.ShippingOptionAdminDto;
import com.dreamy.dto.TradingDtos.ShippingOptionUpsert;
import com.dreamy.dto.TradingDtos.ShippingQuotePreviewRequest;
import com.dreamy.dto.TradingDtos.ShippingQuotePreviewResponse;
import com.dreamy.dto.TradingDtos.TaxRuleEnabledPatch;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台运费选项控制器（order-flow-complete E：/api/admin/shipping/options CRUD + enabled PATCH + quote-preview；
 * RBAC `/shipping`；不缓存）。
 */
@RestController
public class AdminShippingOptionController {

    private static final String PERMISSION = "/shipping";

    private final ShippingOptionAdminService optionAdminService;
    private final ShippingQuotePreviewService previewService;

    public AdminShippingOptionController(ShippingOptionAdminService optionAdminService,
                                         ShippingQuotePreviewService previewService) {
        this.optionAdminService = optionAdminService;
        this.previewService = previewService;
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/shipping/options")
    public ResponseEntity<R<Map<String, List<ShippingOptionAdminDto>>>> list() {
        return ResponseEntity.ok(R.ok(Map.of("items", optionAdminService.list())));
    }

    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/shipping/options")
    public ResponseEntity<R<ShippingOptionAdminDto>> create(@RequestBody ShippingOptionUpsert req) {
        return ResponseEntity.status(201).body(R.ok(optionAdminService.create(req)));
    }

    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/shipping/options/{id}")
    public ResponseEntity<R<ShippingOptionAdminDto>> update(@PathVariable String id,
                                                            @RequestBody ShippingOptionUpsert req) {
        return ResponseEntity.ok(R.ok(optionAdminService.update(id, req)));
    }

    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/shipping/options/{id}/enabled")
    public ResponseEntity<R<ShippingOptionAdminDto>> setEnabled(@PathVariable String id,
                                                                @RequestBody TaxRuleEnabledPatch req) {
        return ResponseEntity.ok(R.ok(optionAdminService.setEnabled(id, req == null ? null : req.enabled())));
    }

    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/shipping/options/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        optionAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** 运费/税费试算（§3.2） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/shipping/quote-preview")
    public ResponseEntity<R<ShippingQuotePreviewResponse>> preview(@RequestBody ShippingQuotePreviewRequest req) {
        return ResponseEntity.ok(R.ok(previewService.preview(req)));
    }
}
