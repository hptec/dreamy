package com.dreamy.domain.cart.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.cart.consts.CartMergeRecordDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 cart_merge_record（匿名车合并幂等记录——决策 8，mergeCart anon_token 去重，TX-TRD-007 幂等闸）。
 * 保留 30 天（SCHED-TRD-003 滚动清理）。
 * L2 TRACE: trading-data-detail §9 DDL-2 / IDX-TRD-016 / RM-TRD-008。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cart_merge_record", comment = "匿名购物车合并幂等记录（决策8）", indexes = {
        @Index(name = "uk_merge_customer_token", columns = {"customer_id", "anon_token"}, unique = true, local = false)
})
@TableName(value = "cart_merge_record", autoResultMap = true)
public class CartMergeRecord extends LongAuditableEntity {

    @Column(name = CartMergeRecordDBConst.CUSTOMER_ID, definition = "bigint NOT NULL")
    private Long customerId;

    @Column(name = CartMergeRecordDBConst.ANON_TOKEN, definition = "varchar(64) NOT NULL COMMENT '前端匿名购物车标识（合并幂等键）'")
    private String anonToken;
}
