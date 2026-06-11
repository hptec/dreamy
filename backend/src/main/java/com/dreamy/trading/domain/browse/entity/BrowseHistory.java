package com.dreamy.trading.domain.browse.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.trading.domain.browse.consts.BrowseHistoryDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 browse_history（Recently Viewed 浏览历史——决策 23；upsert + 每用户 50 条滚动）。
 * L2 TRACE: trading-data-detail §9 DDL-9 / IDX-TRD-019/020 / RM-TRD-070~072。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "browse_history", comment = "Recently Viewed 浏览历史（决策23）", indexes = {
        @Index(name = "uk_browse_customer_product", columns = {"customer_id", "product_id"}, unique = true, local = false),
        @Index(name = "idx_browse_customer_viewed", columns = {"customer_id", "viewed_at"}, unique = false, local = false)
})
@TableName(value = "browse_history", autoResultMap = true)
public class BrowseHistory extends LongAuditableEntity {

    @Column(name = BrowseHistoryDBConst.CUSTOMER_ID, definition = "bigint NOT NULL")
    private Long customerId;

    @Column(name = BrowseHistoryDBConst.PRODUCT_ID, definition = "bigint NOT NULL")
    private Long productId;

    @Column(name = BrowseHistoryDBConst.VIEWED_AT, definition = "datetime(3) NOT NULL COMMENT 'upsert 更新；每用户保留最近 50 条'")
    private LocalDateTime viewedAt;
}
