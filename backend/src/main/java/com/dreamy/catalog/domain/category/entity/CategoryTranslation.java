package com.dreamy.catalog.domain.category.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.catalog.domain.category.consts.CategoryTranslationDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 category_translation（分类多语言附表，locale ∈ {es,fr}，EN 存主表——决策 13）。
 * L2 TRACE: catalog-data-detail §9 DDL-2 / IDX-CAT-012 / CV-CAT-009。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "category_translation", comment = "分类多语言附表", indexes = {
        @Index(name = "uk_ct", columns = {"category_id", "locale"}, unique = true, local = false)
})
@TableName(value = "category_translation", autoResultMap = true)
public class CategoryTranslation extends LongAuditableEntity {

    @Column(name = CategoryTranslationDBConst.CATEGORY_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 category.id'")
    private Long categoryId;

    @Column(name = CategoryTranslationDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT 'es|fr（EN 存主表）'")
    private String locale;

    @Column(name = CategoryTranslationDBConst.NAME, definition = "varchar(64) NULL COMMENT '品类名译文'")
    private String name;
}
