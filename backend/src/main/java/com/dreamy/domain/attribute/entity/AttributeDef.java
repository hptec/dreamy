package com.dreamy.domain.attribute.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.dreamy.domain.attribute.consts.AttributeDefDBConst;
import com.dreamy.enums.AttributeType;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 表 attribute_def（商品属性字典）。key 全局唯一且不可改（V-CAT-053/057）。
 * L2 TRACE: catalog-data-detail §9 DDL-3 / IDX-CAT-013 / TASK-002。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "attribute_def", comment = "商品属性字典", indexes = {
        @Index(name = "uk_attribute_def_key", columns = {"key"}, unique = true, local = false)
})
@TableName(value = "attribute_def", autoResultMap = true)
public class AttributeDef extends LongAuditableEntity {

    /** SQL 保留字列：DML 转义由 @TableField 负责（CP-015） */
    @Column(name = AttributeDefDBConst.KEY, definition = "varchar(64) NOT NULL COMMENT '属性键（小写下划线，不可改）'")
    @TableField("`key`")
    private String key;

    @Column(name = AttributeDefDBConst.LABEL, definition = "varchar(64) NOT NULL COMMENT '显示名(EN 基准)'")
    private String label;

    @Column(name = AttributeDefDBConst.TYPE, definition = "tinyint NOT NULL COMMENT '类型：1=单选 2=多选 3=文本 4=开关'")
    private AttributeType type;

    /** 可选值列表（仅 select/multiselect，V-CAT-056；MAP-CAT-013） */
    @Column(name = AttributeDefDBConst.OPTIONS, definition = "json NULL COMMENT '可选值列表（仅 select/multiselect）'")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> options;

    @Column(name = AttributeDefDBConst.DELETED_AT, definition = "datetime DEFAULT NULL COMMENT '逻辑删除时间'")
    private LocalDateTime deletedAt;
}
