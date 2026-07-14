package com.dreamy.domain.collection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.collection.consts.CollectionGroupDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 collection_group（集合分组；合并承载 er-diagram CustomTag 的 dimension 语义，不建 custom_tag 表）。
 * L2 TRACE: catalog-data-detail §9 DDL-7 / TASK-005 / TASK-026 合并处置。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "collection_group", comment = "集合分组", indexes = {})
@TableName(value = "collection_group", autoResultMap = true)
public class CollectionGroup extends LongAuditableEntity {

    @Column(name = CollectionGroupDBConst.NAME, definition = "varchar(64) NOT NULL COMMENT '分组名(EN 基准)'")
    private String name;

    @Column(name = CollectionGroupDBConst.DESCRIPTION, definition = "varchar(255) NULL COMMENT '分组说明'")
    private String description;

}
