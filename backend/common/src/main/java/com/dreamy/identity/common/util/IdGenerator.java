package com.dreamy.identity.common.util;

import java.util.UUID;

/**
 * ID 生成工具（CHAR(36) UUID 主键）。
 * 约束: shared-contracts type_mapping uuid↔CHAR(36)↔String；entity @TableId(INPUT)。
 */
public final class IdGenerator {

    private IdGenerator() {
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }
}
