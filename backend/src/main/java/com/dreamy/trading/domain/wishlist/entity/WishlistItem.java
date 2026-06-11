package com.dreamy.trading.domain.wishlist.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.trading.domain.wishlist.consts.WishlistItemDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 wishlist_item（收藏清单——决策 18；customer+product 唯一幂等）。
 * L2 TRACE: trading-data-detail §9 DDL-8 / IDX-TRD-018 / RM-TRD-060~063。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "wishlist_item", comment = "收藏清单（决策18）", indexes = {
        @Index(name = "uk_wishlist_customer_product", columns = {"customer_id", "product_id"}, unique = true, local = false)
})
@TableName(value = "wishlist_item", autoResultMap = true)
public class WishlistItem extends LongAuditableEntity {

    @Column(name = WishlistItemDBConst.CUSTOMER_ID, definition = "bigint NOT NULL")
    private Long customerId;

    @Column(name = WishlistItemDBConst.PRODUCT_ID, definition = "bigint NOT NULL")
    private Long productId;
}
