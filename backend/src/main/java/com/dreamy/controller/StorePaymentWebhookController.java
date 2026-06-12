package com.dreamy.controller;

import com.dreamy.domain.payment.service.StripeWebhookService;
import com.dreamy.dto.TradingDtos.WebhookReceived;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe webhook 控制器（trading-api-detail §4；FLOW-P07，决策 7/25）。
 * 传输约束（webhook 安全第 5 条）：仅 POST + JSON；JWT 白名单豁免（application.yml
 * store-public-paths `POST:/api/store/payments/stripe/webhook`）后由 StripeSignatureVerifier
 * 验签链接管（验签失败 401601，不读负载不写库——StripeWebhookService 第一步）。
 * 原始 body 以 String 接收（签名按原始字节负载计算，禁止先经反序列化）。
 */
@RestController
public class StorePaymentWebhookController {

    private final StripeWebhookService stripeWebhookService;

    public StorePaymentWebhookController(StripeWebhookService stripeWebhookService) {
        this.stripeWebhookService = stripeWebhookService;
    }

    /** E-stripeWebhook（200 = 已受理，含幂等重复事件；R 包络 data={received:true}） */
    @PostMapping("/api/store/payments/stripe/webhook")
    public ResponseEntity<R<WebhookReceived>> webhook(
            @RequestHeader(name = "Stripe-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        stripeWebhookService.handle(rawBody, signature);
        return ResponseEntity.ok(R.ok(new WebhookReceived(true)));
    }
}
