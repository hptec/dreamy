package com.dreamy.trading.controller;

import com.dreamy.identity.aspect.RequirePermission;
import com.dreamy.trading.domain.order.service.AdminOrderService;
import com.dreamy.trading.domain.refund.service.RefundService;
import com.dreamy.trading.dto.TradingDtos.AdminOrderDetail;
import com.dreamy.trading.dto.TradingDtos.AdminOrderListItem;
import com.dreamy.trading.dto.TradingDtos.AdminOrderShipRequest;
import com.dreamy.trading.dto.TradingDtos.AdminOrderStatusPatch;
import com.dreamy.trading.dto.TradingDtos.AdminRefundCreate;
import com.dreamy.trading.dto.TradingDtos.AdminRefundDto;
import huihao.page.Paginated;
import huihao.web.R;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 后台订单控制器（trading-api-detail §9，FLOW-P09，RBAC `/orders`；不缓存）。
 */
@RestController
public class AdminOrderController {

    private static final String PERMISSION = "/orders";

    private final AdminOrderService adminOrderService;
    private final RefundService refundService;

    public AdminOrderController(AdminOrderService adminOrderService, RefundService refundService) {
        this.adminOrderService = adminOrderService;
        this.refundService = refundService;
    }

    /** E-listAdminOrders（V-TRD-043~047） */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/orders")
    public ResponseEntity<R<Paginated<AdminOrderListItem>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(R.ok(adminOrderService.list(page, pageSize, status, search, currency, from, to)));
    }

    /** E-getAdminOrder */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/orders/{id}")
    public ResponseEntity<R<AdminOrderDetail>> get(@PathVariable Long id) {
        return ResponseEntity.ok(R.ok(adminOrderService.getDetail(id)));
    }

    /** E-shipAdminOrder（OP-009/s-752；TX-TRD-004a） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/orders/{id}/ship")
    public ResponseEntity<R<AdminOrderDetail>> ship(@PathVariable Long id,
                                                    @RequestBody AdminOrderShipRequest request) {
        return ResponseEntity.ok(R.ok(adminOrderService.ship(id,
                request == null ? null : request.carrier(),
                request == null ? null : request.trackingNo())));
    }

    /** E-patchAdminOrderStatus（TX-TRD-004b） */
    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/orders/{id}/status")
    public ResponseEntity<R<AdminOrderDetail>> patchStatus(@PathVariable Long id,
                                                           @RequestBody AdminOrderStatusPatch request) {
        return ResponseEntity.ok(R.ok(adminOrderService.patchStatus(id,
                request == null ? null : request.status())));
    }

    /** E-createAdminRefund（ALIGN-006「发起退款」；201；TX-TRD-009b） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/orders/{id}/refunds")
    public ResponseEntity<R<AdminRefundDto>> createRefund(@PathVariable Long id,
                                                          @RequestBody AdminRefundCreate request) {
        return ResponseEntity.status(201).body(R.ok(refundService.createAdminRefund(id,
                request == null ? null : request.amount(),
                request == null ? null : request.reason())));
    }
}
