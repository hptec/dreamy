package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 外部网关类型（AI 网关 / 物流网关 / 支付网关）。
 * L2 TRACE: i18n-backend-data-detail.md ExternalGatewayConfig.gateway_type / 决策1。
 */
@Enumable
public enum GatewayType implements IntEnum, Describable {
    AI(1, "AI网关"),
    LOGISTICS(2, "物流网关"),
    PAYMENT(3, "支付网关");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    GatewayType(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static GatewayType of(Integer value) {
        for (GatewayType t : values()) {
            if (t.key.equals(value)) {
                return t;
            }
        }
        return null;
    }
}
