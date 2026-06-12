package com.dreamy.domain.category.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.dreamy.domain.category.consts.CategoryDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 表 category（三层分类树）。基类 LongAuditableEntity（决策 12 Long 自增主键，无逻辑删除——物理删除 + 守卫 409502）。
 * L2 TRACE: catalog-data-detail §1.2 / §9 DDL-1 / IDX-CAT-011 / CV-CAT-005·008 / TASK-001。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "category", comment = "商品标准品类（三层树）", indexes = {
        @Index(name = "idx_category_parent", columns = {"parent_id"}, unique = false, local = false),
        @Index(name = "idx_category_attrset", columns = {"attribute_set_id"}, unique = false, local = false)
})
@TableName(value = "category", autoResultMap = true)
public class Category extends LongAuditableEntity {

    @Column(name = CategoryDBConst.NAME, definition = "varchar(64) NOT NULL COMMENT '品类名称(EN 基准)'")
    private String name;

    @Column(name = CategoryDBConst.PARENT_ID, definition = "bigint NULL COMMENT '父分类，NULL=根（CP-010 逻辑外键）'")
    private Long parentId;

    @Column(name = CategoryDBConst.LEVEL, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '层级 1..3（应用层校验 V-CAT-045）'")
    private Integer level;

    @Column(name = CategoryDBConst.ATTRIBUTE_SET_ID, definition = "bigint NULL COMMENT '绑定属性集（根必填，子可空=沿父链继承）'")
    private Long attributeSetId;

    /** 子分类属性可见性 delta {attrKey: visibility}（saveDrawer 语义，MAP-CAT-013 JacksonTypeHandler） */
    @Column(name = CategoryDBConst.ATTR_OVERRIDES, definition = "json NULL COMMENT '子分类属性可见性 delta {attrKey: visibility}'")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> attrOverrides;

    @Column(name = CategoryDBConst.SORT, definition = "int NOT NULL DEFAULT 0 COMMENT '同层排序（拖拽落库）'")
    private Integer sort;
}
