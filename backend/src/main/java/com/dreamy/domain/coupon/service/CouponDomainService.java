package com.dreamy.domain.coupon.service;

import java.math.BigDecimal;

/**
 * 券域服务（SVC-MKT-01，决策 3 进程内直调；trading 为消费方——CouponPort 的提供侧权威定义）。
 * L2 TRACE: marketing-data-detail §7 / FLOW-P05/P06/P08。
 */
public interface CouponDomainService {

    /**
     * 结算/报价校验（FLOW-P05 STEP-TRD-06 / E-MKT-10 共用内核）。
     * 无效不抛异常，返回 reasonCode（422701/422702/422703）。
     */
    CouponQuote validate(String code, BigDecimal subtotalUsd, String locale);

    /**
     * 下单事务内核销（FLOW-P06；参与 trading TX-TRD-002，本方法不自启事务）。
     * 内部：findByCode → 复跑 validate 状态/窗口判定（防 TOCTOU）→ RM-MKT-107 redeemCas。
     * affected=0 → 抛 MarketingException(COUPON_EXHAUSTED/422703)；
     * 无效/门槛 → 抛 MarketingException(COUPON_INVALID/422701)/(COUPON_MIN_AMOUNT_NOT_MET/422702)；
     * 由 trading 事务整体回滚。
     *
     * @return couponId（落 orders.coupon_id）
     */
    Long redeem(String code, BigDecimal subtotalUsd);

    /** 核销回滚（FLOW-P08 超时取消 / 下单失败补偿；参与调用方事务）。RM-MKT-108。 */
    void rollbackRedeem(Long couponId);

    /** 校验结果（CouponQuote——SVC-MKT-01 返回结构） */
    record CouponQuote(boolean valid, Long couponId, BigDecimal discountUsd, boolean freeShipping,
                       Integer reasonCode, CouponBriefView coupon) {
    }

    /** 券摘要（valid=false 时仅在券存在时返回——不泄露码表，E-MKT-10 STEP-MKT-05） */
    record CouponBriefView(String code, String name, Integer type, String value, BigDecimal minAmount) {
    }
}
