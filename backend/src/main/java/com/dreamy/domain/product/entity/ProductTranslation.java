package com.dreamy.domain.product.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.dreamy.domain.product.consts.ProductTranslationDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 表 product_translation（商品多语言附表，决策 13/17）。
 * FULLTEXT 索引 ft_pt_search(name) WITH PARSER ngram（IDX-CAT-010）由
 * CatalogFulltextIndexInitializer 落地。
 * L2 TRACE: catalog-data-detail §9 DDL-12 / IDX-CAT-010 / CV-CAT-009。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "product_translation", comment = "商品多语言附表（决策13/17）", indexes = {
        @Index(name = "uk_pt", columns = {"product_id", "locale"}, unique = true, local = false)
})
@TableName(value = "product_translation", autoResultMap = true)
public class ProductTranslation extends LongAuditableEntity {

    @Column(name = ProductTranslationDBConst.PRODUCT_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 product.id'")
    private Long productId;

    @Column(name = ProductTranslationDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT 'es|fr'")
    private String locale;

    @Column(name = ProductTranslationDBConst.NAME, definition = "varchar(128) NULL")
    private String name;

    @Column(name = ProductTranslationDBConst.DESCRIPTION, definition = "text NULL")
    private String description;

    @Column(name = ProductTranslationDBConst.SELLING_POINTS, definition = "json NULL COMMENT '翻译卖点（数组，与主表对应）'")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> sellingPoints;

    @Column(name = ProductTranslationDBConst.SEO_TITLE, definition = "varchar(128) NULL")
    private String seoTitle;

    @Column(name = ProductTranslationDBConst.SEO_DESCRIPTION, definition = "varchar(255) NULL")
    private String seoDescription;
}
