package com.dreamy.shipping.controller;

import com.dreamy.identity.aspect.RequirePermission;
import com.dreamy.shipping.domain.carrier.service.CarrierAdminService;
import com.dreamy.shipping.dto.ShippingDtos.CarrierDto;
import com.dreamy.shipping.dto.ShippingDtos.CarrierUpsert;
import com.dreamy.shipping.dto.ShippingDtos.StatusPatch;
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
 * 后台承运方控制器（E-SHP-01~05；V-SHP-001 鉴权前置：AdminJwtFilter(40100) + RBAC `/shipping`(40300)；不缓存）。
 */
@RestController
public class AdminCarrierController {

    private static final String PERMISSION = "/shipping";

    private final CarrierAdminService carrierAdminService;

    public AdminCarrierController(CarrierAdminService carrierAdminService) {
        this.carrierAdminService = carrierAdminService;
    }

    /** E-SHP-01 listAdminCarriers */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/shipping/carriers")
    public ResponseEntity<R<Map<String, List<CarrierDto>>>> list() {
        return ResponseEntity.ok(R.ok(Map.of("items", carrierAdminService.list())));
    }

    /** E-SHP-02 createAdminCarrier（TX-SHP-001，审计 action=创建承运方） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/shipping/carriers")
    public ResponseEntity<R<CarrierDto>> create(@RequestBody CarrierUpsert req) {
        return ResponseEntity.status(201).body(R.ok(carrierAdminService.create(req)));
    }

    /** E-SHP-03 updateAdminCarrier（TX-SHP-002，审计 action=编辑承运方） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/shipping/carriers/{id}")
    public ResponseEntity<R<CarrierDto>> update(@PathVariable String id, @RequestBody CarrierUpsert req) {
        return ResponseEntity.ok(R.ok(carrierAdminService.update(id, req)));
    }

    /** E-SHP-04 deleteAdminCarrier（TX-SHP-003，审计 action=删除承运方）→ 204 */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/shipping/carriers/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        carrierAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** E-SHP-05 toggleAdminCarrierStatus（TX-SHP-004，审计 action=承运方状态变更；carrier_status 状态机） */
    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/shipping/carriers/{id}/status")
    public ResponseEntity<R<CarrierDto>> toggleStatus(@PathVariable String id, @RequestBody StatusPatch req) {
        return ResponseEntity.ok(R.ok(carrierAdminService.toggleStatus(id, req == null ? null : req.status())));
    }
}
