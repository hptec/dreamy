package com.dreamy.catalog.domain.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.catalog.domain.product.consts.SkuDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 sku（颜色x尺码矩阵）。version 乐观锁双用途：本域编辑防丢失（409508，CAS 见 RM-CAT-122）
 * + trading 扣减防超卖（BE-DIM-4，跨域消费方）。CAS 走 setSql 手工版本递增（CP-016），不挂 @Version。
 * L2 TRACE: catalog-data-detail §9 DDL-14 / IDX-CAT-006/007 / CV-CAT-011 / TASK-012。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "sku", comment = "SKU 颜色x尺码矩阵", indexes = {
        @Index(name = "uk_sku_code", columns = {"sku_code"}, unique = true, local = false),
        @Index(name = "idx_sku_product", columns = {"product_id"}, unique = false, local = false)
})
@TableName(value = "sku", autoResultMap = true)
public class Sku extends LongAuditableEntity {

    @Column(name = SkuDBConst.PRODUCT_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 product.id'")
    private Long productId;

    @Column(name = SkuDBConst.SKU_CODE, definition = "varchar(64) NOT NULL COMMENT '^[A-Z0-9-]+$ 全局唯一'")
    private String skuCode;

    @Column(name = SkuDBConst.COLOR, definition = "varchar(32) NOT NULL")
    private String color;

    @Column(name = SkuDBConst.SIZE, definition = "varchar(16) NOT NULL")
    private String size;

    @Column(name = SkuDBConst.STOCK, definition = "int NOT NULL DEFAULT 0 COMMENT '现货库存；定制款不扣减（决策6）'")
    private Integer stock;

    @Column(name = SkuDBConst.VERSION, definition = "bigint NOT NULL DEFAULT 0 COMMENT '乐观锁（扣减防超卖 BE-DIM-4 / 编辑防丢失 409508）'")
    private Long version;
}
