package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 成员指派状态（unassigned|assigned|reminded|ordered，showroom_member_assignment 状态机）。
 * 不接收任何客户端赋值——仅经状态机 CAS 推进（RM-SHR-035/036/037，CV-SHR-002）。
 * L2 TRACE: showroom-data-detail §1.1 枚举落地（CP-003 VARCHAR + Java enum 双保险）。
 */
@Enumable
public enum AssignStatus implements IntEnum, Describable {
    UNASSIGNED(1, "未指派"),
    ASSIGNED(2, "已指派"),
    REMINDED(3, "已提醒"),
    ORDERED(4, "已下单");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    AssignStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /** 契约整数码 → 枚举；未知值返回 null */
    public static AssignStatus of(Integer value) {
        for (AssignStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
