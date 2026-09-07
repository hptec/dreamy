package com.dreamy.infra.stripe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stripe stub 实现（dev 缺省，dreamy.stripe.mode=stub，沿用 identity oidc/smtp stub 风格 DG-002）。
 * 不出网络；create 结果入内存表（status=requires_confirmation，order-flow-complete §4.1：前端据
 * client_secret 后缀 _secret_stub 识别 stub 并调用 POST /orders/{id}/payment/confirm）；
 * markSucceeded 由 StubPaymentConfirmService 在确认后调用；retrieve 回读内存表状态（未知 id 回 succeeded）。
 * client_secret 为 stub 占位（即取即用，不落库）；日志不输出任何密钥。
 */
@Component
@ConditionalOnProperty(name = "dreamy.stripe.mode", havingValue = "stub", matchIfMissing = true)
public class StubStripeClient implements StripeClient {

    private static final Logger log = LoggerFactory.getLogger(StubStripeClient.class);

    private final Map<String, StripePaymentIntent> intents = new ConcurrentHashMap<>();

    @Override
    public String mode() {
        return "stub";
    }

    @Override
    public StripePaymentIntent createPaymentIntent(long amountMinor, String currency, String orderNo,
                                                   Map<String, String> metadata) {
        String id = "pi_stub_" + UUID.randomUUID().toString().replace("-", "");
        StripePaymentIntent intent = new StripePaymentIntent(
                id, id + "_secret_stub", "requires_confirmation", amountMinor, currency);
        intents.put(id, intent);
        log.info("[STRIPE-STUB] createPaymentIntent id={} amount_minor={} currency={} order_no={}",
                id, amountMinor, currency, orderNo);
        return intent;
    }

    @Override
    public StripePaymentIntent retrievePaymentIntent(String paymentIntentId) {
        StripePaymentIntent created = intents.get(paymentIntentId);
        StripePaymentIntent result = created == null
                ? new StripePaymentIntent(paymentIntentId, null, "succeeded", 0L, "usd")
                : created;
        log.info("[STRIPE-STUB] retrievePaymentIntent id={} status={}", paymentIntentId, result.status());
        return result;
    }

    /** stub 支付确认：内存表 PI 置 succeeded（order-flow-complete §4.1 第 5 步） */
    public void markSucceeded(String paymentIntentId) {
        intents.computeIfPresent(paymentIntentId, (id, pi) ->
                new StripePaymentIntent(pi.id(), pi.clientSecret(), "succeeded", pi.amountMinor(), pi.currency()));
        log.info("[STRIPE-STUB] markSucceeded id={}", paymentIntentId);
    }

    @Override
    public StripePaymentIntent cancelPaymentIntent(String paymentIntentId) {
        StripePaymentIntent created = intents.get(paymentIntentId);
        StripePaymentIntent result = new StripePaymentIntent(paymentIntentId,
                created == null ? null : created.clientSecret(), "canceled",
                created == null ? 0L : created.amountMinor(),
                created == null ? "usd" : created.currency());
        intents.put(paymentIntentId, result);
        log.info("[STRIPE-STUB] cancelPaymentIntent id={}", paymentIntentId);
        return result;
    }

    @Override
    public StripeRefund createRefund(String paymentIntentId, Long amountMinor, String reason) {
        StripePaymentIntent created = intents.get(paymentIntentId);
        long amount = amountMinor != null ? amountMinor : (created == null ? 0L : created.amountMinor());
        String currency = created == null ? "usd" : created.currency();
        String id = "re_stub_" + UUID.randomUUID().toString().replace("-", "");
        log.info("[STRIPE-STUB] createRefund id={} payment_intent={} amount_minor={} reason={}",
                id, paymentIntentId, amount, reason);
        return new StripeRefund(id, "succeeded", amount, currency);
    }
}
