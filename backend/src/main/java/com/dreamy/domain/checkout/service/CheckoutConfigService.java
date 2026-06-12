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
 * custom_refund_grace_hours 影响后续退款资格判定（判定时实时读取）。
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
        CheckoutConfig config = checkoutConfigRepository.getSingleton();
        return new CheckoutConfigDto(config.getGiftWrapFeeUsd(), config.getCustomRefundGraceHours());
    }

    /** E-updateAdminCheckoutConfig（V-TRD-060/061 + TX-TRD-012） */
    public CheckoutConfigDto update(BigDecimal giftWrapFeeUsd, Integer customRefundGraceHours) {
        TradingFieldErrors errors = new TradingFieldErrors();
        if (giftWrapFeeUsd == null || giftWrapFeeUsd.signum() < 0) {
            errors.reject("gift_wrap_fee_usd", giftWrapFeeUsd == null ? "required" : "range_invalid");
        }
        if (customRefundGraceHours == null || customRefundGraceHours < 1 || customRefundGraceHours > 168) {
            errors.reject("custom_refund_grace_hours",
                    customRefundGraceHours == null ? "required" : "range_invalid");
        }
        errors.throwIfAny();
        CheckoutConfig before = checkoutConfigRepository.getSingleton();
        String beforeJson = snapshot(before);
        txRunner.inTx(() -> {
            CheckoutConfig config = new CheckoutConfig();
            config.setGiftWrapFeeUsd(giftWrapFeeUsd);
            config.setCustomRefundGraceHours(customRefundGraceHours);
            checkoutConfigRepository.update(config);
            audit.record(TradingAuditRecorder.ACTION_CHECKOUT_CONFIG, "checkout_config",
                    "{\"before\":" + beforeJson + ",\"after\":"
                            + "{\"gift_wrap_fee_usd\":\"" + giftWrapFeeUsd
                            + "\",\"custom_refund_grace_hours\":" + customRefundGraceHours + "}}");
        });
        return get();
    }

    private String snapshot(CheckoutConfig config) {
        return "{\"gift_wrap_fee_usd\":\"" + config.getGiftWrapFeeUsd()
                + "\",\"custom_refund_grace_hours\":" + config.getCustomRefundGraceHours() + "}";
    }
}
