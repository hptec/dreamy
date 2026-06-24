package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * Newsletter 订阅来源（footer|modal|exit_intent|home_block）。
 * L2 TRACE: MAP-MKT-013 / V-MKT-010 / CV-MKT-001。
 * KD-13：新增 HOME_BLOCK(4) 用于 site_builder 域首页 Newsletter 区块订阅。
 */
@Enumable
public enum NewsletterSource implements IntEnum, Describable {
    FOOTER(1, "页脚"),
    MODAL(2, "弹窗"),
    EXIT_INTENT(3, "退出挽留"),
    HOME_BLOCK(4, "首页区块");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    NewsletterSource(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static NewsletterSource of(Integer value) {
        for (NewsletterSource s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
