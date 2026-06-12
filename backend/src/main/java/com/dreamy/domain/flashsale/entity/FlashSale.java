package com.dreamy.domain.flashsale.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.enums.FlashSaleStatus;
import com.dreamy.domain.flashsale.consts.FlashSaleDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 flash_sale（闪购活动，限时秒杀）。四态 status 由 SCHED-MKT-01 翻转 active/ended（s-761 自动下线）。
 * L2 TRACE: marketing-data-detail §1.2/§11 DDL-15 / IDX-MKT-003 / TASK-022 / TASK-043 flash_sale_lifecycle / TASK-060。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "flash_sale", comment = "闪购活动（限时秒杀）", indexes = {
        @Index(name = "idx_flash_status_end", columns = {"status", "end_at"}, unique = false, local = false)
})
@TableName(value = "flash_sale", autoResultMap = true)
public class FlashSale extends LongAuditableEntity {

    @Column(name = FlashSaleDBConst.NAME, definition = "varchar(64) NOT NULL COMMENT '活动名(EN 基准)'")
    private String name;

    @Column(name = FlashSaleDBConst.DISCOUNT, definition = "varchar(32) NOT NULL COMMENT '如 最高 40% OFF'")
    private String discount;

    @Column(name = FlashSaleDBConst.START_AT, definition = "datetime(3) NOT NULL")
    private LocalDateTime startAt;

    @Column(name = FlashSaleDBConst.END_AT, definition = "datetime(3) NOT NULL COMMENT 'js_guard end_at>start_at；到期 SCHED 自动 ended（s-761）'")
    private LocalDateTime endAt;

    @Column(name = FlashSaleDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=草稿 2=已排期 3=进行中 4=已结束'")
    private FlashSaleStatus status;
}
