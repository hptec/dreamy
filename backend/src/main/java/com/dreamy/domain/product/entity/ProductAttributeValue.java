package com.dreamy.domain.product.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.product.consts.ProductAttributeValueDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 product_attribute_value（商品动态属性 EAV：multiselect 一值一行；toggle 存 "true"/"false"；text ≤255）。
 * idx_pav_filter 含 product_id 形成覆盖索引（PLP attr 筛选 EXISTS 子查询免回表）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "product_attribute_value", comment = "商品动态属性值（EAV）", indexes = {
        @Index(name = "uk_pav", columns = {"product_id", "attribute_id", "value"}, unique = true, local = false),
        @Index(name = "idx_pav_filter", columns = {"attribute_id", "value", "product_id"}, unique = false, local = false)
})
@TableName(value = "product_attribute_value", autoResultMap = true)
public class ProductAttributeValue extends LongAuditableEntity {

    @Column(name = ProductAttributeValueDBConst.PRODUCT_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 product.id'")
    private Long productId;

    @Column(name = ProductAttributeValueDBConst.ATTRIBUTE_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 attribute_def.id'")
    private Long attributeId;

    /** SQL 保留字列：DML 转义由 @TableField 负责（CP-015，同 attribute_def.key） */
    @Column(name = ProductAttributeValueDBConst.VALUE, definition = "varchar(255) NOT NULL COMMENT '属性值（EN 规范值）'")
    @TableField("`value`")
    private String value;
}
