package com.dreamy.domain.collection.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.enums.CollectionStatus;
import com.dreamy.domain.collection.consts.CollectionDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 collection（营销集合；合并承载 CustomTag，不建 custom_tag 表）。
 * L2 TRACE: catalog-data-detail §9 DDL-9 / IDX-CAT-016 / TASK-006 / TASK-026 / TASK-035 collection_lifecycle。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "collection", comment = "营销集合", indexes = {
        @Index(name = "idx_collection_group", columns = {"collection_group_id"}, unique = false, local = false),
        @Index(name = "idx_collection_status", columns = {"status"}, unique = false, local = false)
})
@TableName(value = "collection", autoResultMap = true)
public class Collection extends LongAuditableEntity {

    @Column(name = CollectionDBConst.COLLECTION_GROUP_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 collection_group.id'")
    private Long collectionGroupId;

    @Column(name = CollectionDBConst.NAME, definition = "varchar(64) NOT NULL COMMENT '集合名(EN 基准)'")
    private String name;

    @Column(name = CollectionDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=禁用'")
    private CollectionStatus status;

    @Column(name = CollectionDBConst.DELETED_AT, definition = "datetime DEFAULT NULL COMMENT '逻辑删除时间'")
    private LocalDateTime deletedAt;
}
