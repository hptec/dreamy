package com.dreamy.catalog.domain.tag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.catalog.domain.enums.TagStatus;
import com.dreamy.catalog.domain.tag.consts.TagDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 tag（自定义营销标签；合并承载 CustomTag，不建 custom_tag 表）。
 * L2 TRACE: catalog-data-detail §9 DDL-9 / IDX-CAT-016 / TASK-006 / TASK-026 / TASK-035 tag_lifecycle。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "tag", comment = "自定义营销标签", indexes = {
        @Index(name = "idx_tag_dimension", columns = {"dimension_id"}, unique = false, local = false),
        @Index(name = "idx_tag_status", columns = {"status"}, unique = false, local = false)
})
@TableName(value = "tag", autoResultMap = true)
public class Tag extends LongAuditableEntity {

    @Column(name = TagDBConst.DIMENSION_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 tag_dimension.id'")
    private Long dimensionId;

    @Column(name = TagDBConst.NAME, definition = "varchar(64) NOT NULL COMMENT '标签名(EN 基准)'")
    private String name;

    @Column(name = TagDBConst.COVER, definition = "varchar(512) NULL COMMENT '封面图 URL（预签名上传 public_url），空=纯文字'")
    private String cover;

    @Column(name = TagDBConst.STATUS, definition = "varchar(16) NOT NULL DEFAULT 'enabled' COMMENT 'enabled|disabled'")
    private TagStatus status;
}
