package com.dreamy.trading.domain.cart.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.dreamy.trading.domain.cart.consts.CartItemDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 表 cart_item（购物车条目，登录用户 DB 持久化——决策 8）。
 * 双模式不变量（CV-TRD-007 应用层强制）：sku_id XOR custom_size_data（现货行必有 sku_id，定制行必有定制数据）。
 * custom_size_hash：L2 设计为 STORED 生成列，L3 落地为应用计算普通列（TradingParams.customSizeHash，
 * 固定键序规范化 JSON SHA-256），合并判定语义一致（RM-TRD-003）。
 * L2 TRACE: trading-data-detail §9 DDL-1 / IDX-TRD-014/015 / TASK-016。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cart_item", comment = "购物车条目（决策8）", indexes = {
        @Index(name = "idx_cart_customer", columns = {"customer_id"}, unique = false, local = false),
        @Index(name = "idx_cart_customer_sku", columns = {"customer_id", "sku_id"}, unique = false, local = false)
})
@TableName(value = "cart_item", autoResultMap = true)
public class CartItem extends LongAuditableEntity {

    @Column(name = CartItemDBConst.CUSTOMER_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 user.id（BE-DIM-6 隔离）'")
    private Long customerId;

    @Column(name = CartItemDBConst.PRODUCT_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 product.id'")
    private Long productId;

    @Column(name = CartItemDBConst.SKU_ID, definition = "bigint NULL COMMENT '现货必填；定制款 NULL（决策6）'")
    private Long skuId;

    @Column(name = CartItemDBConst.QTY, definition = "int NOT NULL COMMENT '数量 >=1'")
    private Integer qty;

    @Column(name = CartItemDBConst.CUSTOM_SIZE_DATA, definition = "json NULL COMMENT '定制尺寸 {bust,waist,hips,hollow_to_floor,height?}'")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, BigDecimal> customSizeData;

    @Column(name = CartItemDBConst.CUSTOM_SIZE_HASH, definition = "char(64) NULL COMMENT '定制数据合并判定哈希（规范化 JSON SHA-256，应用计算）'")
    private String customSizeHash;
}
