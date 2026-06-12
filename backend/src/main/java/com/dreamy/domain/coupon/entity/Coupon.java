package com.dreamy.domain.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.coupon.consts.CouponDBConst;
import com.dreamy.enums.CouponStatus;
import com.dreamy.enums.CouponType;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 表 coupon（优惠券：折扣/满减/包邮）。code 唯一大写归一（CV-MKT-008）；value 按 type 可解析串（DEC-MKT-4）；
 * total_limit 缺省 100000=不限（DEC-MKT-5）；used_count 仅 RM-MKT-107/108 核销 CAS/回滚可写；
 * 五态 status 由 SCHED-MKT-01 翻转（DEC-MKT-3）；EN description 列（DEC-MKT-1）。
 * L2 TRACE: marketing-data-detail §1.2/§11 DDL-13 / IDX-MKT-001/002 / TASK-021 / TASK-042 coupon_lifecycle。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "coupon", comment = "优惠券（折扣/满减/包邮）", indexes = {
        @Index(name = "uk_coupon_code", columns = {"code"}, unique = true, local = false),
        @Index(name = "idx_coupon_status", columns = {"status"}, unique = false, local = false)
})
@TableName(value = "coupon", autoResultMap = true)
public class Coupon extends LongAuditableEntity {

    @Column(name = CouponDBConst.CODE, definition = "varchar(32) NOT NULL COMMENT '券码 ^[A-Z0-9]+$ 唯一（大写归一）'")
    private String code;

    @Column(name = CouponDBConst.NAME, definition = "varchar(64) NOT NULL COMMENT '券名(EN 基准)'")
    private String name;

    @Column(name = CouponDBConst.TYPE, definition = "tinyint NOT NULL COMMENT '类型：1=折扣 2=固定金额 3=免运费'")
    private CouponType type;

    @Column(name = CouponDBConst.VALUE, definition = "varchar(32) NOT NULL COMMENT '展示串，按 type pattern 可解析（DEC-MKT-4）'")
    private String value;

    @Column(name = CouponDBConst.MIN_AMOUNT, definition = "decimal(12,2) NOT NULL DEFAULT 0 COMMENT '门槛金额 USD 基准'")
    private BigDecimal minAmount;

    @Column(name = CouponDBConst.TOTAL_LIMIT, definition = "int NOT NULL DEFAULT 100000 COMMENT '限量；>9999 视为不限（DEC-MKT-5）'")
    private Integer totalLimit;

    @Column(name = CouponDBConst.USED_COUNT, definition = "int NOT NULL DEFAULT 0 COMMENT '核销计数，仅 RM-MKT-107/108 可写'")
    private Integer usedCount;

    @Column(name = CouponDBConst.START_AT, definition = "datetime(3) NULL")
    private LocalDateTime startAt;

    @Column(name = CouponDBConst.END_AT, definition = "datetime(3) NULL COMMENT 'js_guard end_at>start_at'")
    private LocalDateTime endAt;

    @Column(name = CouponDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=草稿 2=已排期 3=生效中 4=即将过期 5=已过期（SCHED 翻转）'")
    private CouponStatus status;

    @Column(name = CouponDBConst.DESCRIPTION, definition = "varchar(255) NULL COMMENT '券说明(EN 基准，DEC-MKT-1)'")
    private String description;
}
