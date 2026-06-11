package com.dreamy.trading.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 金额工具（决策 14/15/28：USD 基准 → 订单币种换算，HALF_UP 2 位；Stripe 金额最小货币单位）。
 * 恒等式 js_guard（CV-TRD-003）：total = subtotal + shipping_fee + gift_wrap_fee − discount_amount。
 * L2 TRACE: trading-api-detail §0 金额约定 / TC-TRD-001/002。
 */
public final class Money {

    private Money() {
    }

    /** USD 基准金额 × 汇率 → 订单币种（HALF_UP 2 位） */
    public static BigDecimal toCurrency(BigDecimal usdAmount, BigDecimal rate) {
        if (usdAmount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return usdAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 行单价解析（MAP-TRD-003 口径）：multi_currency_prices[currency] 覆盖价优先，否则 USD×rate。
     */
    public static BigDecimal unitPrice(BigDecimal priceUsd, Map<String, BigDecimal> multiCurrencyPrices,
                                       String currency, BigDecimal rate) {
        if (multiCurrencyPrices != null) {
            BigDecimal covered = multiCurrencyPrices.get(currency);
            if (covered != null) {
                return covered.setScale(2, RoundingMode.HALF_UP);
            }
        }
        return toCurrency(priceUsd, rate);
    }

    /** Stripe 金额 = 订单币种金额 × 100 取整（五币种均为 2 位小数币，决策 14） */
    public static long toMinor(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** 金额恒等式（CV-TRD-003 js_guard：服务端自算，违反抛 50000 级内部错误） */
    public static BigDecimal total(BigDecimal subtotal, BigDecimal shippingFee, BigDecimal giftWrapFee,
                                   BigDecimal discountAmount) {
        BigDecimal total = subtotal.add(shippingFee).add(giftWrapFee).subtract(discountAmount);
        if (total.signum() < 0) {
            throw new IllegalStateException("amount identity violated: negative total");
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
