package com.dreamy.catalog.domain.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.catalog.domain.product.consts.ProductTagDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 product_tag（Product-Tag nm 关系，AdminProductUpsert.tag_ids 落点）。
 * 无审计需求仍用基类（成本可忽略，保持范式统一——catalog-data-detail §1.2）。
 * L2 TRACE: catalog-data-detail §9 DDL-16 / IDX-CAT-019 / TASK-006。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "product_tag", comment = "商品-标签挂载", indexes = {
        @Index(name = "uk_ptag", columns = {"product_id", "tag_id"}, unique = true, local = false),
        @Index(name = "idx_ptag_tag", columns = {"tag_id"}, unique = false, local = false)
})
@TableName(value = "product_tag", autoResultMap = true)
public class ProductTag extends LongAuditableEntity {

    @Column(name = ProductTagDBConst.PRODUCT_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 product.id'")
    private Long productId;

    @Column(name = ProductTagDBConst.TAG_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 tag.id'")
    private Long tagId;
}
