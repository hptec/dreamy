package com.dreamy.catalog.domain.attribute.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.dreamy.catalog.domain.attribute.consts.AttributeDefTranslationDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 表 attribute_def_translation（属性字典多语言附表）。options 译文与主表等长（CV-CAT-007/V-CAT-058）。
 * L2 TRACE: catalog-data-detail §9 DDL-4 / IDX-CAT-014。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "attribute_def_translation", comment = "属性字典多语言附表", indexes = {
        @Index(name = "uk_adt", columns = {"attribute_def_id", "locale"}, unique = true, local = false)
})
@TableName(value = "attribute_def_translation", autoResultMap = true)
public class AttributeDefTranslation extends LongAuditableEntity {

    @Column(name = AttributeDefTranslationDBConst.ATTRIBUTE_DEF_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 attribute_def.id'")
    private Long attributeDefId;

    @Column(name = AttributeDefTranslationDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT 'es|fr'")
    private String locale;

    @Column(name = AttributeDefTranslationDBConst.LABEL, definition = "varchar(64) NULL COMMENT '显示名译文'")
    private String label;

    @Column(name = AttributeDefTranslationDBConst.OPTIONS, definition = "json NULL COMMENT '与主表 options 等长的译文数组'")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> options;
}
