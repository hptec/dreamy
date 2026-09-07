package com.dreamy.domain.checkout.service;

import com.dreamy.domain.checkout.entity.CheckoutConfig;
import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.dto.TradingDtos.CheckoutConfigDto;
import com.dreamy.infra.TradingAuditRecorder;
import com.dreamy.infra.TradingTxRunner;
import com.dreamy.support.TradingFieldErrors;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 结算配置服务（trading-api-detail §12，决策 24/28；derived_scope host TASK-054）。
 * TX-TRD-012：update + operation_log 同事务。
 * 生效语义：gift_wrap_fee_usd 影响后续报价/下单（既有订单为快照）；
 * custom_refund_grace_hours 影响后续退款资格判定（判定时实时读取）；
 * order-flow-complete：pending_timeout_minutes 影响后续下单 expires_at；auto_complete_days/auto_deliver_days
 * 由 OrderAutoCompleteScheduler 每轮实时读取；exchange_rate_spread_scaled/production_days_default 由 P1-B 报价读取。
 */
@Service
public class CheckoutConfigService {

    private final CheckoutConfigRepository checkoutConfigRepository;
    private final TradingTxRunner txRunner;
    private final TradingAuditRecorder audit;

    public CheckoutConfigService(CheckoutConfigRepository checkoutConfigRepository, TradingTxRunner txRunner,
                                 TradingAuditRecorder audit) {
        this.checkoutConfigRepository = checkoutConfigRepository;
        this.txRunner = txRunner;
        this.audit = audit;
    }

    /** E-getAdminCheckoutConfig */
    public CheckoutConfigDto get() {
        return toDto(checkoutConfigRepository.getSingleton());
    }

    /** 兼容旧签名（仅礼品包装费 + 宽限期；其余字段保持现值） */
    public CheckoutConfigDto update(BigDecimal giftWrapFeeUsd, Integer customRefundGraceHours) {
        CheckoutConfig current = checkoutConfigRepository.getSingleton();
        return update(new CheckoutConfigDto(giftWrapFeeUsd, customRefundGraceHours, current.getAutoCompleteDays(),
                current.getAutoDeliverDays(), current.getPendingTimeoutMinutes(),
                current.getExchangeRateSpreadScaled(), current.getProductionDaysDefault()));
    }

    /**
     * E-updateAdminCheckoutConfig（V-TRD-060/061 + TX-TRD-012；order-flow-complete §2.3 新字段范围校验：
     * auto_complete_days 1..60 / auto_deliver_days 1..120 / pending_timeout_minutes 5..1440 /
     * exchange_rate_spread_scaled 0..2000 / production_days_default 1..180）。
     */
    public CheckoutConfigDto update(CheckoutConfigDto request) {
        CheckoutConfigDto req = request == null ? new CheckoutConfigDto(null, null, null, null, null, null, null)
                : request;
        TradingFieldErrors errors = new TradingFieldErrors();
        if (req.giftWrapFeeUsd() == null || req.giftWrapFeeUsd().signum() < 0) {
            errors.reject("gift_wrap_fee_usd", req.giftWrapFeeUsd() == null ? "required" : "range_invalid");
        }
        checkRange(errors, "custom_refund_grace_hours", req.customRefundGraceHours(), 1, 168);
        checkRange(errors, "auto_complete_days", req.autoCompleteDays(), 1, 60);
        checkRange(errors, "auto_deliver_days", req.autoDeliverDays(), 1, 120);
        checkRange(errors, "pending_timeout_minutes", req.pendingTimeoutMinutes(), 5, 1440);
        checkRange(errors, "exchange_rate_spread_scaled", req.exchangeRateSpreadScaled(), 0, 2000);
        checkRange(errors, "production_days_default", req.productionDaysDefault(), 1, 180);
        errors.throwIfAny();
        CheckoutConfig before = checkoutConfigRepository.getSingleton();
        String beforeJson = snapshot(before);
        txRunner.inTx(() -> {
            CheckoutConfig config = new CheckoutConfig();
            config.setGiftWrapFeeUsd(req.giftWrapFeeUsd());
            config.setCustomRefundGraceHours(req.customRefundGraceHours());
            config.setAutoCompleteDays(req.autoCompleteDays());
            config.setAutoDeliverDays(req.autoDeliverDays());
            config.setPendingTimeoutMinutes(req.pendingTimeoutMinutes());
            config.setExchangeRateSpreadScaled(req.exchangeRateSpreadScaled());
            config.setProductionDaysDefault(req.productionDaysDefault());
            checkoutConfigRepository.update(config);
            audit.record(TradingAuditRecorder.ACTION_CHECKOUT_CONFIG, "checkout_config",
                    "{\"before\":" + beforeJson + ",\"after\":" + snapshot(config) + "}");
        });
        return get();
    }

    private static void checkRange(TradingFieldErrors errors, String field, Integer value, int min, int max) {
        if (value == null) {
            errors.reject(field, "required");
        } else if (value < min || value > max) {
            errors.reject(field, "range_invalid");
        }
    }

    static CheckoutConfigDto toDto(CheckoutConfig config) {
        return new CheckoutConfigDto(config.getGiftWrapFeeUsd(), config.getCustomRefundGraceHours(),
                config.getAutoCompleteDays(), config.getAutoDeliverDays(), config.getPendingTimeoutMinutes(),
                config.getExchangeRateSpreadScaled(), config.getProductionDaysDefault());
    }

    private String snapshot(CheckoutConfig config) {
        return "{\"gift_wrap_fee_usd\":\"" + config.getGiftWrapFeeUsd()
                + "\",\"custom_refund_grace_hours\":" + config.getCustomRefundGraceHours()
                + ",\"auto_complete_days\":" + config.getAutoCompleteDays()
                + ",\"auto_deliver_days\":" + config.getAutoDeliverDays()
                + ",\"pending_timeout_minutes\":" + config.getPendingTimeoutMinutes()
                + ",\"exchange_rate_spread_scaled\":" + config.getExchangeRateSpreadScaled()
                + ",\"production_days_default\":" + config.getProductionDaysDefault() + "}";
    }
}
