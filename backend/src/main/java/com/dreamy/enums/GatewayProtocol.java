package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 网关协议类型（OpenAI-compatible / 其他协议预留扩展）。
 * L2 TRACE: i18n-backend-data-detail.md ExternalGatewayConfig.protocol / 决策1。
 */
@Enumable
public enum GatewayProtocol implements IntEnum, Describable {
    OPENAI_COMPATIBLE(1, "OpenAI兼容"),
    ANTHROPIC(2, "Anthropic"),
    CUSTOM(3, "自定义");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    GatewayProtocol(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static GatewayProtocol of(Integer value) {
        for (GatewayProtocol t : values()) {
            if (t.key.equals(value)) {
                return t;
            }
        }
        return null;
    }
}
