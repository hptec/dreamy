package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/** 订单时间线事件类型 */
@Enumable
public enum OrderEventType implements IntEnum, Describable {
    STATUS_CHANGED(1, "状态变更"),
    NOTE(2, "备注"),
    SHIPMENT(3, "发货/物流"),
    PAYMENT(4, "支付"),
    REFUND(5, "退款"),
    EMAIL(6, "邮件"),
    PRODUCTION(7, "制作阶段");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    OrderEventType(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static OrderEventType of(Integer value) {
        for (OrderEventType s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
