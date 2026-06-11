package com.dreamy.shipping.controller;

import com.dreamy.identity.aspect.RequirePermission;
import com.dreamy.shipping.domain.rate.service.ShippingRateAdminService;
import com.dreamy.shipping.dto.ShippingDtos.ShippingRateDto;
import com.dreamy.shipping.dto.ShippingDtos.ShippingRateUpsert;
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
 * 后台运费规则控制器（E-SHP-06~09；V-SHP-001 鉴权前置：AdminJwtFilter(40100) + RBAC `/shipping`(40300)；不缓存）。
 */
@RestController
public class AdminShippingRateController {

    private static final String PERMISSION = "/shipping";

    private final ShippingRateAdminService rateAdminService;

    public AdminShippingRateController(ShippingRateAdminService rateAdminService) {
        this.rateAdminService = rateAdminService;
    }

    /** E-SHP-06 listAdminShippingRates */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/shipping/rates")
    public ResponseEntity<R<Map<String, List<ShippingRateDto>>>> list() {
        return ResponseEntity.ok(R.ok(Map.of("items", rateAdminService.list())));
    }

    /** E-SHP-07 createAdminShippingRate（TX-SHP-005，审计 action=创建运费规则） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/shipping/rates")
    public ResponseEntity<R<ShippingRateDto>> create(@RequestBody ShippingRateUpsert req) {
        return ResponseEntity.status(201).body(R.ok(rateAdminService.create(req)));
    }

    /** E-SHP-08 updateAdminShippingRate（TX-SHP-006，审计 action=编辑运费规则） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/shipping/rates/{id}")
    public ResponseEntity<R<ShippingRateDto>> update(@PathVariable String id, @RequestBody ShippingRateUpsert req) {
        return ResponseEntity.ok(R.ok(rateAdminService.update(id, req)));
    }

    /** E-SHP-09 deleteAdminShippingRate（TX-SHP-007，审计 action=删除运费规则）→ 204 */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/shipping/rates/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        rateAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
