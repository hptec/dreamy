package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * Newsletter 订阅状态（方案 A 双态；取代 KD-12 单态 subscribed）。
 * 退订后可复活：重新订阅时 status 2→1 且 unsubscribed_at 清空。
 */
@Enumable
public enum SubscriberStatus implements IntEnum, Describable {
    SUBSCRIBED(1, "已订阅"),
    UNSUBSCRIBED(2, "已退订");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    SubscriberStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static SubscriberStatus of(Integer value) {
        for (SubscriberStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
