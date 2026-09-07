package com.dreamy.controller;

import com.dreamy.domain.payment.service.StubPaymentConfirmService;
import com.dreamy.dto.TradingDtos.StoreOrderDetail;
import huihao.web.R;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * stub 支付确认控制器（order-flow-complete §3.1 A）：仅 dreamy.stripe.mode=stub 注册——real 模式下端点根本
 * 不存在（404），不受任何其他开关影响。入参为空，金额/币种/locale 全部取自服务端记录。
 */
@RestController
@ConditionalOnProperty(name = "dreamy.stripe.mode", havingValue = "stub", matchIfMissing = true)
public class StorePaymentConfirmController {

    private final StubPaymentConfirmService stubPaymentConfirmService;

    public StorePaymentConfirmController(StubPaymentConfirmService stubPaymentConfirmService) {
        this.stubPaymentConfirmService = stubPaymentConfirmService;
    }

    /** E-confirmStubPayment（PENDING → PAID；非 PENDING 409602；返回最新 StoreOrderDetail） */
    @PostMapping("/api/store/orders/{id}/payment/confirm")
    public ResponseEntity<R<StoreOrderDetail>> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(R.ok(stubPaymentConfirmService.confirm(StoreAuth.customerId(), id)));
    }
}
