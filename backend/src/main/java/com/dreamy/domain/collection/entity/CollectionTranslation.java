package com.dreamy.domain.collection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.collection.consts.CollectionTranslationDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 collection_translation（集合多语言附表；合并承载 CustomTagTranslation）。
 * L2 TRACE: catalog-data-detail §9 DDL-10 / IDX-CAT-017 / TASK-026。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "collection_translation", comment = "集合多语言附表", indexes = {
        @Index(name = "uk_ct", columns = {"collection_id", "locale"}, unique = true, local = false)
})
@TableName(value = "collection_translation", autoResultMap = true)
public class CollectionTranslation extends LongAuditableEntity {

    @Column(name = CollectionTranslationDBConst.COLLECTION_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 collection.id'")
    private Long collectionId;

    @Column(name = CollectionTranslationDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT 'es|fr'")
    private String locale;

    @Column(name = CollectionTranslationDBConst.LABEL, definition = "varchar(64) NULL COMMENT '集合名译文'")
    private String label;
}
