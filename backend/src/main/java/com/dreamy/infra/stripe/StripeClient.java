package com.dreamy.infra.stripe;

import java.util.Map;

/**
 * Stripe 客户端端口（trading §0 StripePort 的基建落点，BE-DIM-5 防腐层）。
 * 失败 → StripeUnavailableException（502601）；超时 → StripeTimeoutException（504601）；
 * 降级矩阵：下单订单保持 pending 可重试，退款审核事务整体回滚（error-strategy 降级矩阵）。
 * webhook 验签独立于本端口（StripeSignatureVerifier）。
 */
public interface StripeClient {

    /**
     * 创建 PaymentIntent（FLOW-P06 下单 / payment-intent 重试入口）。
     *
     * @param amountMinor 最小货币单位金额（total_amount × 100 取整，决策 14）
     * @param currency    订单币种小写（usd/eur/cad/aud/gbp）
     * @param orderNo     订单号（写入 metadata，便于 webhook 对账）
     * @param metadata    附加元数据（可空）
     */
    StripePaymentIntent createPaymentIntent(long amountMinor, String currency, String orderNo,
                                            Map<String, String> metadata);

    /** 查询 PaymentIntent（webhook 幂等汇合/状态核对） */
    StripePaymentIntent retrievePaymentIntent(String paymentIntentId);

    /** 取消 PaymentIntent（FLOW-P08 超时取消回补） */
    StripePaymentIntent cancelPaymentIntent(String paymentIntentId);

    /**
     * 创建 Refund（FLOW-P10 退款审核通过 / 迟到支付自动退款补偿）。
     *
     * @param paymentIntentId 原支付意图
     * @param amountMinor     退款金额（最小货币单位）；null=全额
     * @param reason          Stripe reason（可空：requested_by_customer 等）
     */
    StripeRefund createRefund(String paymentIntentId, Long amountMinor, String reason);
}
