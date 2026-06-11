package com.dreamy.catalog.domain.tag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.catalog.domain.tag.consts.TagDimensionTranslationDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 tag_dimension_translation（标签维度多语言附表）。
 * L2 TRACE: catalog-data-detail §9 DDL-8 / IDX-CAT-018。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "tag_dimension_translation", comment = "标签维度多语言附表", indexes = {
        @Index(name = "uk_tdt", columns = {"tag_dimension_id", "locale"}, unique = true, local = false)
})
@TableName(value = "tag_dimension_translation", autoResultMap = true)
public class TagDimensionTranslation extends LongAuditableEntity {

    @Column(name = TagDimensionTranslationDBConst.TAG_DIMENSION_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 tag_dimension.id'")
    private Long tagDimensionId;

    @Column(name = TagDimensionTranslationDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT 'es|fr'")
    private String locale;

    @Column(name = TagDimensionTranslationDBConst.NAME, definition = "varchar(64) NULL COMMENT '维度名译文'")
    private String name;
}
