package com.dreamy.catalog.domain.tag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.catalog.domain.tag.consts.TagTranslationDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 tag_translation（标签多语言附表；合并承载 CustomTagTranslation）。
 * L2 TRACE: catalog-data-detail §9 DDL-10 / IDX-CAT-017 / TASK-026。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "tag_translation", comment = "标签多语言附表", indexes = {
        @Index(name = "uk_tt", columns = {"tag_id", "locale"}, unique = true, local = false)
})
@TableName(value = "tag_translation", autoResultMap = true)
public class TagTranslation extends LongAuditableEntity {

    @Column(name = TagTranslationDBConst.TAG_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 tag.id'")
    private Long tagId;

    @Column(name = TagTranslationDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT 'es|fr'")
    private String locale;

    @Column(name = TagTranslationDBConst.LABEL, definition = "varchar(64) NULL COMMENT '标签名译文'")
    private String label;
}
