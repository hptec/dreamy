package com.dreamy.controller;

import com.dreamy.domain.order.service.StoreOrderService;
import com.dreamy.domain.refund.service.RefundService;
import com.dreamy.dto.TradingDtos.PaymentCredential;
import com.dreamy.dto.TradingDtos.StoreOrderDetail;
import com.dreamy.dto.TradingDtos.StoreOrderListItem;
import com.dreamy.dto.TradingDtos.StoreRefundApply;
import com.dreamy.dto.TradingDtos.StoreRefundDto;
import huihao.page.Paginated;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费端订单控制器（trading-api-detail §5：listStoreOrders/getStoreOrder/cancelStoreOrder/
 * retryOrderPayment/applyStoreRefund；user_id 强隔离 BE-DIM-6）。
 */
@RestController
public class StoreOrderController {

    private final StoreOrderService storeOrderService;
    private final RefundService refundService;

    public StoreOrderController(StoreOrderService storeOrderService, RefundService refundService) {
        this.storeOrderService = storeOrderService;
        this.refundService = refundService;
    }

    /** E-listStoreOrders（Paginated 六字段） */
    @GetMapping("/api/store/orders")
    public ResponseEntity<R<Paginated<StoreOrderListItem>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) Integer status) {
        return ResponseEntity.ok(R.ok(storeOrderService.listOrders(StoreAuth.customerId(), page, pageSize, status)));
    }

    /** E-getStoreOrder（404601 防探测） */
    @GetMapping("/api/store/orders/{id}")
    public ResponseEntity<R<StoreOrderDetail>> get(@PathVariable Long id) {
        return ResponseEntity.ok(R.ok(storeOrderService.getOrderDetail(StoreAuth.customerId(), id)));
    }

    /** E-cancelStoreOrder（TX-TRD-005） */
    @PostMapping("/api/store/orders/{id}/cancel")
    public ResponseEntity<R<StoreOrderDetail>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(R.ok(storeOrderService.cancelOrder(StoreAuth.customerId(), id)));
    }

    /** E-retryOrderPayment（410601/409602 矩阵） */
    @PostMapping("/api/store/orders/{id}/payment-intent")
    public ResponseEntity<R<PaymentCredential>> retryPayment(@PathVariable Long id) {
        return ResponseEntity.ok(R.ok(storeOrderService.retryPayment(StoreAuth.customerId(), id)));
    }

    /** E-applyStoreRefund（201；TX-TRD-009a） */
    @PostMapping("/api/store/orders/{id}/refunds")
    public ResponseEntity<R<StoreRefundDto>> applyRefund(@PathVariable Long id,
                                                         @RequestBody StoreRefundApply request) {
        return ResponseEntity.status(201).body(R.ok(refundService.applyStoreRefund(StoreAuth.customerId(), id,
                request == null ? null : request.reason())));
    }
}
