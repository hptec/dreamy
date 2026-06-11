package com.dreamy.trading.domain.order.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.dreamy.trading.domain.order.consts.OrderLineDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 表 order_line（订单行快照：商品/SKU/价格/图 + custom_size_data 定制行判定依据——决策 24）。
 * 仅存快照不校验后续商品存在性（CV-TRD-011）。
 * L2 TRACE: trading-data-detail §9 DDL-5 / IDX-TRD-013 / TASK-018。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "order_line", comment = "订单行快照", indexes = {
        @Index(name = "idx_line_order", columns = {"order_id"}, unique = false, local = false),
        @Index(name = "idx_line_product", columns = {"product_id"}, unique = false, local = false)
})
@TableName(value = "order_line", autoResultMap = true)
public class OrderLine extends LongAuditableEntity {

    @Column(name = OrderLineDBConst.ORDER_ID, definition = "bigint NOT NULL")
    private Long orderId;

    @Column(name = OrderLineDBConst.PRODUCT_ID, definition = "bigint NOT NULL")
    private Long productId;

    @Column(name = OrderLineDBConst.SKU_ID, definition = "bigint NULL COMMENT '定制款 NULL'")
    private Long skuId;

    @Column(name = OrderLineDBConst.PRODUCT_NAME, definition = "varchar(128) NOT NULL COMMENT '快照'")
    private String productName;

    @Column(name = OrderLineDBConst.SKU_CODE, definition = "varchar(64) NULL")
    private String skuCode;

    @Column(name = OrderLineDBConst.COLOR, definition = "varchar(32) NULL")
    private String color;

    @Column(name = OrderLineDBConst.SIZE, definition = "varchar(16) NULL")
    private String size;

    @Column(name = OrderLineDBConst.QTY, definition = "int NOT NULL")
    private Integer qty;

    @Column(name = OrderLineDBConst.UNIT_PRICE, definition = "decimal(12,2) NOT NULL COMMENT '订单币种单价快照'")
    private BigDecimal unitPrice;

    @Column(name = OrderLineDBConst.IMG, definition = "varchar(512) NULL COMMENT '快照图'")
    private String img;

    @Column(name = OrderLineDBConst.CUSTOM_SIZE_DATA, definition = "json NULL COMMENT '定制行判定依据（决策24）'")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, BigDecimal> customSizeData;
}
