package com.dreamy.domain.lookbook.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import com.dreamy.enums.PublishStatus;
import com.dreamy.domain.lookbook.consts.LookbookDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 lookbook（Lookbook 主题画册）。EN description 列（DEC-MKT-1）。
 * L2 TRACE: marketing-data-detail §1.2/§11 DDL-8 / IDX-MKT-009 / TASK-023 / TASK-044 lookbook_publish。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "lookbook", comment = "Lookbook 主题画册", indexes = {
        @Index(name = "idx_lookbook_status", columns = {"status"}, unique = false, local = false)
})
@TableName(value = "lookbook", autoResultMap = true)
public class Lookbook extends LongAuditableEntity {

    @Column(name = LookbookDBConst.TITLE, definition = "varchar(128) NOT NULL COMMENT '画册标题(EN 基准)'")
    private String title;

    @Column(name = LookbookDBConst.THEME, definition = "varchar(32) NULL COMMENT 'Vineyard/Beach/Forest'")
    private String theme;

    @Column(name = LookbookDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=草稿 2=已发布'")
    private PublishStatus status;

    @Column(name = LookbookDBConst.DESCRIPTION, definition = "varchar(500) NULL COMMENT '画册描述(EN 基准，DEC-MKT-1)'")
    private String description;

    @Column(name = LookbookDBConst.DELETED_AT, definition = "datetime DEFAULT NULL COMMENT '逻辑删除时间'")
    private LocalDateTime deletedAt;
}
