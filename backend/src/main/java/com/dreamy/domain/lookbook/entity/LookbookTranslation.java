package com.dreamy.domain.lookbook.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.lookbook.consts.LookbookTranslationDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 lookbook_translation（Lookbook 多语言附表，locale ∈ {es,fr}）。
 * L2 TRACE: marketing-data-detail §11 DDL-9 / IDX-MKT-014。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "lookbook_translation", comment = "Lookbook 多语言附表", indexes = {
        @Index(name = "uk_lbt", columns = {"lookbook_id", "locale"}, unique = true, local = false)
})
@TableName(value = "lookbook_translation", autoResultMap = true)
public class LookbookTranslation extends LongAuditableEntity {

    @Column(name = LookbookTranslationDBConst.LOOKBOOK_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 lookbook.id'")
    private Long lookbookId;

    @Column(name = LookbookTranslationDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT 'es|fr'")
    private String locale;

    @Column(name = LookbookTranslationDBConst.TITLE, definition = "varchar(128) NULL")
    private String title;

    @Column(name = LookbookTranslationDBConst.DESCRIPTION, definition = "varchar(500) NULL")
    private String description;
}
