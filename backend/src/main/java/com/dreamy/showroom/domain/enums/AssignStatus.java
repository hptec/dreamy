package com.dreamy.showroom.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 成员指派状态（unassigned|assigned|reminded|ordered，showroom_member_assignment 状态机）。
 * 不接收任何客户端赋值——仅经状态机 CAS 推进（RM-SHR-035/036/037，CV-SHR-002）。
 * L2 TRACE: showroom-data-detail §1.1 枚举落地（CP-003 VARCHAR + Java enum 双保险）。
 */
public enum AssignStatus implements StrEnum {
    UNASSIGNED("unassigned"),
    ASSIGNED("assigned"),
    REMINDED("reminded"),
    ORDERED("ordered");

    @JsonValue
    @Getter
    private final String key;

    AssignStatus(String key) {
        this.key = key;
    }

    /** 契约字符串 → 枚举；未知值返回 null */
    public static AssignStatus of(String value) {
        for (AssignStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
