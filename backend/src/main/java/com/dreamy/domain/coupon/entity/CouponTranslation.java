package com.dreamy.domain.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.coupon.consts.CouponTranslationDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 coupon_translation（优惠券多语言附表，locale ∈ {es,fr}）。
 * L2 TRACE: marketing-data-detail §11 DDL-14 / IDX-MKT-016。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "coupon_translation", comment = "优惠券多语言附表", indexes = {
        @Index(name = "uk_cpt", columns = {"coupon_id", "locale"}, unique = true, local = false)
})
@TableName(value = "coupon_translation", autoResultMap = true)
public class CouponTranslation extends LongAuditableEntity {

    @Column(name = CouponTranslationDBConst.COUPON_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 coupon.id'")
    private Long couponId;

    @Column(name = CouponTranslationDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT 'es|fr'")
    private String locale;

    @Column(name = CouponTranslationDBConst.NAME, definition = "varchar(64) NULL")
    private String name;

    @Column(name = CouponTranslationDBConst.DESCRIPTION, definition = "varchar(255) NULL")
    private String description;
}
