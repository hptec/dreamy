package com.dreamy.domain.collection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.collection.consts.CollectionGroupTranslationDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 collection_group_translation（集合分组多语言附表）。
 * L2 TRACE: catalog-data-detail §9 DDL-8 / IDX-CAT-018。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "collection_group_translation", comment = "集合分组多语言附表", indexes = {
        @Index(name = "uk_cgt", columns = {"collection_group_id", "locale"}, unique = true, local = false)
})
@TableName(value = "collection_group_translation", autoResultMap = true)
public class CollectionGroupTranslation extends LongAuditableEntity {

    @Column(name = CollectionGroupTranslationDBConst.COLLECTION_GROUP_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 collection_group.id'")
    private Long collectionGroupId;

    @Column(name = CollectionGroupTranslationDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT 'es|fr'")
    private String locale;

    @Column(name = CollectionGroupTranslationDBConst.NAME, definition = "varchar(64) NULL COMMENT '分组名译文'")
    private String name;
}
