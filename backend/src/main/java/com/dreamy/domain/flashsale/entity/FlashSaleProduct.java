package com.dreamy.domain.flashsale.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.flashsale.consts.FlashSaleProductDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 flash_sale_product（闪购-商品挂载 nm——逻辑外键 catalog.product，CP-010 / CV-MKT-005/006）。
 * L2 TRACE: marketing-data-detail §11 DDL-17 / IDX-MKT-018。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "flash_sale_product", comment = "闪购-商品挂载", indexes = {
        @Index(name = "uk_fsp", columns = {"flash_sale_id", "product_id"}, unique = true, local = false),
        @Index(name = "idx_fsp_product", columns = {"product_id"}, unique = false, local = false)
})
@TableName(value = "flash_sale_product", autoResultMap = true)
public class FlashSaleProduct extends LongAuditableEntity {

    @Column(name = FlashSaleProductDBConst.FLASH_SALE_ID, definition = "bigint NOT NULL")
    private Long flashSaleId;

    @Column(name = FlashSaleProductDBConst.PRODUCT_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 catalog.product.id'")
    private Long productId;
}
