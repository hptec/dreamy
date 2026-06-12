package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.refund.service.RefundService;
import com.dreamy.dto.TradingDtos.AdminRefundApprove;
import com.dreamy.dto.TradingDtos.AdminRefundDto;
import com.dreamy.dto.TradingDtos.AdminRefundPatch;
import com.dreamy.dto.TradingDtos.AdminRefundReject;
import huihao.page.Paginated;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台退款工单控制器（trading-api-detail §10，FLOW-P10，决策 24/31，s-755；RBAC `/refunds`）。
 */
@RestController
public class AdminRefundController {

    private static final String PERMISSION = "/refunds";

    private final RefundService refundService;

    public AdminRefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    /** E-listAdminRefunds（V-TRD-054） */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/refunds")
    public ResponseEntity<R<Paginated<AdminRefundDto>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(R.ok(refundService.pageAdmin(page, pageSize, status, search)));
    }

    /** E-approveAdminRefund（TX-TRD-003：Stripe 事务内整体回滚） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/refunds/{id}/approve")
    public ResponseEntity<R<AdminRefundDto>> approve(@PathVariable Long id,
                                                     @RequestBody(required = false) AdminRefundApprove request) {
        return ResponseEntity.ok(R.ok(refundService.approve(id,
                request == null ? null : request.returnTrackingNo())));
    }

    /** E-rejectAdminRefund（TX-TRD-009c） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/refunds/{id}/reject")
    public ResponseEntity<R<AdminRefundDto>> reject(@PathVariable Long id,
                                                    @RequestBody AdminRefundReject request) {
        return ResponseEntity.ok(R.ok(refundService.reject(id, request == null ? null : request.reason())));
    }

    /** E-patchAdminRefund（决策 31 登记退货单号；登记类操作不发 MQ） */
    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/refunds/{id}")
    public ResponseEntity<R<AdminRefundDto>> patch(@PathVariable Long id,
                                                   @RequestBody AdminRefundPatch request) {
        return ResponseEntity.ok(R.ok(refundService.patchReturnTrackingNo(id,
                request == null ? null : request.returnTrackingNo())));
    }
}
