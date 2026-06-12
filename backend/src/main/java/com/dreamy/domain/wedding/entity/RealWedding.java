package com.dreamy.domain.wedding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.enums.PublishStatus;
import com.dreamy.domain.wedding.consts.RealWeddingDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 real_wedding（真实婚礼案例，Shop the Look）。EN 文案列 title/story（DEC-MKT-1）。
 * L2 TRACE: marketing-data-detail §1.2/§11 DDL-5 / IDX-MKT-008 / TASK-025 / TASK-046 real_wedding_publish。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "real_wedding", comment = "真实婚礼案例（Shop the Look）", indexes = {
        @Index(name = "idx_wedding_status", columns = {"status", "wedding_date"}, unique = false, local = false)
})
@TableName(value = "real_wedding", autoResultMap = true)
public class RealWedding extends LongAuditableEntity {

    @Column(name = RealWeddingDBConst.COUPLE, definition = "varchar(64) NOT NULL COMMENT '如 Emma & James'")
    private String couple;

    @Column(name = RealWeddingDBConst.LOCATION, definition = "varchar(128) NULL")
    private String location;

    @Column(name = RealWeddingDBConst.THEME, definition = "varchar(32) NULL")
    private String theme;

    @Column(name = RealWeddingDBConst.WEDDING_DATE, definition = "varchar(16) NULL COMMENT '如 2025-06'")
    private String weddingDate;

    @Column(name = RealWeddingDBConst.COVER, definition = "varchar(512) NULL")
    private String cover;

    @Column(name = RealWeddingDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=草稿 2=已发布'")
    private PublishStatus status;

    @Column(name = RealWeddingDBConst.TITLE, definition = "varchar(200) NULL COMMENT '案例标题(EN 基准，DEC-MKT-1)'")
    private String title;

    @Column(name = RealWeddingDBConst.STORY, definition = "text NULL COMMENT '婚礼故事(EN 基准)'")
    private String story;
}
