package com.dreamy.domain.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.enums.ImageKind;
import com.dreamy.domain.product.consts.ProductImageDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 product_image（商品媒体素材；kind=gallery sort=0 为主图，CV-CAT-010）。
 * L2 TRACE: catalog-data-detail §9 DDL-13 / IDX-CAT-008 / TASK-011。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "product_image", comment = "商品媒体素材", indexes = {
        @Index(name = "idx_image_product_sort", columns = {"product_id", "sort"}, unique = false, local = false)
})
@TableName(value = "product_image", autoResultMap = true)
public class ProductImage extends LongAuditableEntity {

    @Column(name = ProductImageDBConst.PRODUCT_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 product.id'")
    private Long productId;

    @Column(name = ProductImageDBConst.URL, definition = "varchar(512) NOT NULL COMMENT '预签名上传 public_url'")
    private String url;

    @Column(name = ProductImageDBConst.KIND, definition = "tinyint NOT NULL COMMENT '类型：1=商品图 2=场景图 3=视频 4=色板'")
    private ImageKind kind;

    @Column(name = ProductImageDBConst.COLOR_NAME, definition = "varchar(32) NULL COMMENT 'kind=swatch 时颜色名'")
    private String colorName;

    @Column(name = ProductImageDBConst.SORT, definition = "int NOT NULL DEFAULT 0 COMMENT 'gallery sort=0 为主图'")
    private Integer sort;
}
