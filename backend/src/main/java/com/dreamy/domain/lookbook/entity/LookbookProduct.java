package com.dreamy.domain.lookbook.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.lookbook.consts.LookbookProductDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 lookbook_product（Lookbook-商品挂载 nm——逻辑外键 catalog.product，CP-010 / CV-MKT-005/006）。
 * L2 TRACE: marketing-data-detail §11 DDL-10 / IDX-MKT-020。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "lookbook_product", comment = "Lookbook-商品挂载", indexes = {
        @Index(name = "uk_lbp", columns = {"lookbook_id", "product_id"}, unique = true, local = false),
        @Index(name = "idx_lbp_product", columns = {"product_id"}, unique = false, local = false)
})
@TableName(value = "lookbook_product", autoResultMap = true)
public class LookbookProduct extends LongAuditableEntity {

    @Column(name = LookbookProductDBConst.LOOKBOOK_ID, definition = "bigint NOT NULL")
    private Long lookbookId;

    @Column(name = LookbookProductDBConst.PRODUCT_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 catalog.product.id'")
    private Long productId;
}
