package com.dreamy.domain.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.product.consts.ProductCareInstructionDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 product_care_instruction（商品-护理标签关联，多对多）。
 * 复合主键 (product_id, care_id) 防重复挂载。
 * L2 TRACE: catalog-fabric-care-data-detail §1.2 Entity Design / §9 DDL / IDX-FC-005~006。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "product_care_instruction", comment = "商品-护理标签关联（多对多）", indexes = {
        @Index(name = "idx_pci_care", columns = {"care_id"}, unique = false, local = false)
})
@TableName(value = "product_care_instruction", autoResultMap = true)
public class ProductCareInstruction extends LongAuditableEntity {

    @Column(name = ProductCareInstructionDBConst.PRODUCT_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 product.id'")
    private Long productId;

    @Column(name = ProductCareInstructionDBConst.CARE_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 care_instruction_def.id'")
    private Long careId;

    @Column(name = ProductCareInstructionDBConst.SORT_ORDER, definition = "int NULL COMMENT 'PDP 展示顺序'")
    private Integer sortOrder;
}
