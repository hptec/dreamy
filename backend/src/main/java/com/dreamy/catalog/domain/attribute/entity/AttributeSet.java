package com.dreamy.catalog.domain.attribute.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.catalog.domain.attribute.consts.AttributeSetDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 attribute_set（属性集）。
 * L2 TRACE: catalog-data-detail §9 DDL-5 / TASK-003。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "attribute_set", comment = "属性集", indexes = {})
@TableName(value = "attribute_set", autoResultMap = true)
public class AttributeSet extends LongAuditableEntity {

    @Column(name = AttributeSetDBConst.LABEL, definition = "varchar(64) NOT NULL COMMENT '属性集名称'")
    private String label;
}
