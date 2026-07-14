package com.dreamy.domain.guide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.enums.PublishStatus;
import com.dreamy.domain.guide.consts.GuideDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 guide（备婚指南，按阶段）。phase 文本 'Phase 1'.. 字典序即阶段序（E-MKT-08）；EN body 列（DEC-MKT-1）。
 * L2 TRACE: marketing-data-detail §1.2/§11 DDL-11 / IDX-MKT-010 / TASK-024 / TASK-045 guide_publish。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "guide", comment = "备婚指南（按阶段）", indexes = {
        @Index(name = "idx_guide_status_phase", columns = {"status", "phase"}, unique = false, local = false)
})
@TableName(value = "guide", autoResultMap = true)
public class Guide extends LongAuditableEntity {

    @Column(name = GuideDBConst.PHASE, definition = "varchar(32) NOT NULL COMMENT '备婚阶段，如 Phase 1'")
    private String phase;

    @Column(name = GuideDBConst.TIMEFRAME, definition = "varchar(64) NULL COMMENT '如 12+ months out'")
    private String timeframe;

    @Column(name = GuideDBConst.TITLE, definition = "varchar(128) NOT NULL COMMENT '指南标题(EN 基准)'")
    private String title;

    @Column(name = GuideDBConst.TASKS_COUNT, definition = "int NOT NULL DEFAULT 0 COMMENT '待办任务数'")
    private Integer tasksCount;

    @Column(name = GuideDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=草稿 2=已发布'")
    private PublishStatus status;

    @Column(name = GuideDBConst.BODY, definition = "text NULL COMMENT '指南正文(EN 基准，DEC-MKT-1)'")
    private String body;

}
