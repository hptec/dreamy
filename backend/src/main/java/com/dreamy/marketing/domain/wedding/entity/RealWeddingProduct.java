package com.dreamy.marketing.domain.wedding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.marketing.domain.wedding.consts.RealWeddingProductDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 real_wedding_product（案例-商品挂载 nm，Shop the Look——逻辑外键 catalog.product，CP-010 / CV-MKT-005/006）。
 * L2 TRACE: marketing-data-detail §11 DDL-7 / IDX-MKT-019。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "real_wedding_product", comment = "案例-商品挂载", indexes = {
        @Index(name = "uk_rwp", columns = {"real_wedding_id", "product_id"}, unique = true, local = false),
        @Index(name = "idx_rwp_product", columns = {"product_id"}, unique = false, local = false)
})
@TableName(value = "real_wedding_product", autoResultMap = true)
public class RealWeddingProduct extends LongAuditableEntity {

    @Column(name = RealWeddingProductDBConst.REAL_WEDDING_ID, definition = "bigint NOT NULL")
    private Long realWeddingId;

    @Column(name = RealWeddingProductDBConst.PRODUCT_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 catalog.product.id'")
    private Long productId;
}
