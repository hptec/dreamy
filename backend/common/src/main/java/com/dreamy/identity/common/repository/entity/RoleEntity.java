package com.dreamy.identity.common.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 表 role（角色 / 聚合根）。对应 identity-ddl.sql 表 7。
 * 约束: RM-060/061、MAP-005、is_locked 超管保护（FLOW-11/EDGE-014）。
 */
@Data
@TableName("role")
public class RoleEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;

    /** type: preset/custom（ck_role_type） */
    private String type;

    private Boolean isLocked;

    @Version
    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
