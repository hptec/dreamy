package com.dreamy.domain.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.product.consts.ProductFabricCompositionDBConst;
import com.dreamy.enums.FabricLayer;
import com.dreamy.enums.FabricMaterial;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 表 product_fabric_composition（商品面料成分，支持多层次结构）。
 * percentage 总和约束（js_guard）：每个 product_id + layer 组合的所有行 percentage 总和必须=100。
 * L2 TRACE: catalog-fabric-care-data-detail §1.2 Entity Design / §9 DDL / IDX-FC-001。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "product_fabric_composition", comment = "商品面料成分（支持多层次结构）", indexes = {
        @Index(name = "idx_pfc_product_layer", columns = {"product_id", "layer"}, unique = false, local = false)
})
@TableName(value = "product_fabric_composition", autoResultMap = true)
public class ProductFabricComposition extends LongAuditableEntity {

    @Column(name = ProductFabricCompositionDBConst.PRODUCT_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 product.id'")
    private Long productId;

    @Column(name = ProductFabricCompositionDBConst.LAYER, definition = "tinyint NOT NULL COMMENT '层次：1=Shell/2=Lining/3=Overlay/4=Trim'")
    private FabricLayer layer;

    @Column(name = ProductFabricCompositionDBConst.MATERIAL, definition = "tinyint NOT NULL COMMENT '材质：1=Cotton/2=Polyester/.../10=Nylon'")
    private FabricMaterial material;

    @Column(name = ProductFabricCompositionDBConst.PERCENTAGE, definition = "decimal(5,2) NOT NULL COMMENT '百分比 0..100（每层总和必须=100%，js_guard）'")
    private BigDecimal percentage;

    @Column(name = ProductFabricCompositionDBConst.SORT_ORDER, definition = "int NULL COMMENT '同层排序（可空，缺省按提交顺序）'")
    private Integer sortOrder;
}
