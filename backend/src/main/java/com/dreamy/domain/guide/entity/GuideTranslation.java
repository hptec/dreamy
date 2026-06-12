package com.dreamy.domain.guide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.guide.consts.GuideTranslationDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 guide_translation（指南多语言附表，locale ∈ {es,fr}）。
 * L2 TRACE: marketing-data-detail §11 DDL-12 / IDX-MKT-015。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "guide_translation", comment = "指南多语言附表", indexes = {
        @Index(name = "uk_gt", columns = {"guide_id", "locale"}, unique = true, local = false)
})
@TableName(value = "guide_translation", autoResultMap = true)
public class GuideTranslation extends LongAuditableEntity {

    @Column(name = GuideTranslationDBConst.GUIDE_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 guide.id'")
    private Long guideId;

    @Column(name = GuideTranslationDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT 'es|fr'")
    private String locale;

    @Column(name = GuideTranslationDBConst.TITLE, definition = "varchar(128) NULL")
    private String title;

    @Column(name = GuideTranslationDBConst.BODY, definition = "text NULL")
    private String body;
}
