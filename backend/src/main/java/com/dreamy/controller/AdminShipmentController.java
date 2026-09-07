package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.shipment.service.ShipmentService;
import com.dreamy.dto.TradingDtos.ShipmentCreateRequest;
import com.dreamy.dto.TradingDtos.ShipmentDto;
import com.dreamy.dto.TradingDtos.ShipmentEventCreate;
import com.dreamy.dto.TradingDtos.ShipmentPatch;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台包裹控制器（order-flow-complete §3.2：创建/修改/轨迹/签收/作废/同步；RBAC `/orders`；不缓存）。
 * 错误码：422906 超量、409908 重复单号、409906 锁冲突、409909 包裹状态不允许、404907 包裹不存在。
 */
@RestController
public class AdminShipmentController {

    private static final String PERMISSION = "/orders";

    private final ShipmentService shipmentService;

    public AdminShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    /** POST /api/admin/orders/{id}/shipments（201；Idempotency-Key 可选） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/orders/{id}/shipments")
    public ResponseEntity<R<ShipmentDto>> create(@PathVariable Long id, @RequestBody ShipmentCreateRequest request,
                                                 @RequestHeader(name = "Idempotency-Key", required = false) String idemKey) {
        return ResponseEntity.status(201).body(R.ok(shipmentService.create(id, request, idemKey)));
    }

    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/shipments/{id}")
    public ResponseEntity<R<ShipmentDto>> patch(@PathVariable Long id, @RequestBody ShipmentPatch request) {
        return ResponseEntity.ok(R.ok(shipmentService.patch(id, request)));
    }

    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/shipments/{id}/events")
    public ResponseEntity<R<ShipmentDto>> addEvent(@PathVariable Long id, @RequestBody ShipmentEventCreate request) {
        return ResponseEntity.status(201).body(R.ok(shipmentService.addEvent(id, request)));
    }

    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/shipments/{id}/deliver")
    public ResponseEntity<R<ShipmentDto>> deliver(@PathVariable Long id) {
        return ResponseEntity.ok(R.ok(shipmentService.deliver(id)));
    }

    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/shipments/{id}/cancel")
    public ResponseEntity<R<ShipmentDto>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(R.ok(shipmentService.cancel(id)));
    }

    /** provider=stub → 204 无操作 */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/shipments/{id}/sync")
    public ResponseEntity<R<ShipmentDto>> sync(@PathVariable Long id) {
        ShipmentDto result = shipmentService.sync(id);
        if (result == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(R.ok(result));
    }
}
