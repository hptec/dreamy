package com.dreamy.domain.tag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.tag.consts.TagDimensionDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 tag_dimension（自定义标签维度；合并承载 er-diagram CustomTag 的 dimension 语义，不建 custom_tag 表）。
 * L2 TRACE: catalog-data-detail §9 DDL-7 / TASK-005 / TASK-026 合并处置。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "tag_dimension", comment = "自定义标签维度", indexes = {})
@TableName(value = "tag_dimension", autoResultMap = true)
public class TagDimension extends LongAuditableEntity {

    @Column(name = TagDimensionDBConst.NAME, definition = "varchar(64) NOT NULL COMMENT '维度名(EN 基准)'")
    private String name;

    @Column(name = TagDimensionDBConst.DESCRIPTION, definition = "varchar(255) NULL COMMENT '维度说明'")
    private String description;
}
