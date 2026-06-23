package com.dreamy.domain.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.product.consts.ProductCollectionDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 product_collection（Product-Collection nm 关系，AdminProductUpsert.collection_ids 落点）。
 * 无审计需求仍用基类（成本可忽略，保持范式统一——catalog-data-detail §1.2）。
 * L2 TRACE: catalog-data-detail §9 DDL-16 / IDX-CAT-019 / TASK-006。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "product_collection", comment = "商品-集合挂载", indexes = {
        @Index(name = "uk_pcol", columns = {"product_id", "collection_id"}, unique = true, local = false),
        @Index(name = "idx_pcol_collection", columns = {"collection_id"}, unique = false, local = false),
        @Index(name = "idx_pcol_collection_sort", columns = {"collection_id", "sort"}, unique = false, local = false)
})
@TableName(value = "product_collection", autoResultMap = true)
public class ProductCollection extends LongAuditableEntity {

    @Column(name = ProductCollectionDBConst.PRODUCT_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 product.id'")
    private Long productId;

    @Column(name = ProductCollectionDBConst.COLLECTION_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 collection.id'")
    private Long collectionId;

    @Column(name = ProductCollectionDBConst.SORT, definition = "int NOT NULL DEFAULT 0 COMMENT '集合内商品显示顺序（小在前）'")
    private Integer sort;
}
