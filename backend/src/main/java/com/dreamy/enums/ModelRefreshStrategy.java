package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 模型刷新策略（手动 / 定时自刷新）。
 * L2 TRACE: i18n-backend-data-detail.md ExternalGatewayConfig.model_refresh_strategy / 决策5。
 */
@Enumable
public enum ModelRefreshStrategy implements IntEnum, Describable {
    MANUAL(1, "手动刷新"),
    SCHEDULED(2, "定时自动");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    ModelRefreshStrategy(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static ModelRefreshStrategy of(Integer value) {
        for (ModelRefreshStrategy t : values()) {
            if (t.key.equals(value)) {
                return t;
            }
        }
        return null;
    }
}
