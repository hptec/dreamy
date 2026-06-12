package com.dreamy.domain.banner.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.banner.consts.BannerTranslationDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 banner_translation（Banner 多语言附表，locale ∈ {es,fr}，EN 存主表——决策 13 / CV-MKT-007）。
 * L2 TRACE: marketing-data-detail §11 DDL-2 / IDX-MKT-011。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "banner_translation", comment = "Banner 多语言附表", indexes = {
        @Index(name = "uk_bt", columns = {"banner_id", "locale"}, unique = true, local = false)
})
@TableName(value = "banner_translation", autoResultMap = true)
public class BannerTranslation extends LongAuditableEntity {

    @Column(name = BannerTranslationDBConst.BANNER_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 banner.id'")
    private Long bannerId;

    @Column(name = BannerTranslationDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT 'es|fr（EN 存主表）'")
    private String locale;

    @Column(name = BannerTranslationDBConst.TITLE, definition = "varchar(255) NULL")
    private String title;

    @Column(name = BannerTranslationDBConst.SUBTITLE, definition = "varchar(255) NULL")
    private String subtitle;

    @Column(name = BannerTranslationDBConst.CTA_TEXT, definition = "varchar(64) NULL")
    private String ctaText;
}
