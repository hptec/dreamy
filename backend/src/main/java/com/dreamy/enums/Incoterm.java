package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/** 目的国税费承担方式（DDP 结算时预收；DDU 仅提示由收件人向海关缴纳） */
@Enumable
public enum Incoterm implements IntEnum, Describable {
    DDP(1, "DDP 含税到门"),
    DDU(2, "DDU 未含税");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    Incoterm(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static Incoterm of(Integer value) {
        for (Incoterm s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
