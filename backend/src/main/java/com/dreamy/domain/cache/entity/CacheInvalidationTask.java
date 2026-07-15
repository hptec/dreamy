package com.dreamy.domain.cache.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Durable cache invalidation intent and its aggregate execution result. */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cache_invalidation_task", comment = "缓存失效任务", indexes = {
        @Index(name = "idx_cache_task_status_due", columns = {"status", "next_retry_at", "scheduled_at"}),
        @Index(name = "idx_cache_task_resource", columns = {"resource_type", "resource_id"}),
        @Index(name = "idx_cache_task_triggered_at", columns = {"triggered_at"}),
        @Index(name = "idx_cache_task_correlation", columns = {"correlation_id"})
})
@TableName(value = "cache_invalidation_task", autoResultMap = true)
public class CacheInvalidationTask extends LongAuditableEntity {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SUCCEEDED = 1;
    public static final int STATUS_FAILED = 2;
    public static final int STATUS_SCHEDULED = 3;
    public static final int STATUS_RUNNING = 4;
    public static final int STATUS_PARTIAL = 5;
    public static final int STATUS_CANCELLED = 6;
    public static final int STATUS_SKIPPED = 7;
    public static final int STATUS_RETRYING = 8;

    @Column(name = "correlation_id", definition = "varchar(36) NOT NULL COMMENT '跨步骤关联 ID'")
    private String correlationId;

    @Column(name = "trigger_mode", definition = "varchar(20) NOT NULL COMMENT 'BUSINESS_WRITE/MANUAL/SCHEDULED/SYSTEM_EVENT'")
    private String triggerMode;

    @Column(name = "trigger_point", definition = "varchar(100) NOT NULL COMMENT '业务触发点'")
    private String triggerPoint;

    @Column(name = "resource_type", definition = "varchar(40) NOT NULL COMMENT '资源类型'")
    private String resourceType;

    @Column(name = "resource_id", definition = "varchar(64) NULL COMMENT '资源 ID 或批次标识'")
    private String resourceId;

    @Column(name = "resource_label", definition = "varchar(255) NULL COMMENT '资源展示名'")
    private String resourceLabel;

    @Column(name = "targets", definition = "json NOT NULL COMMENT '缓存目标列表'")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> targets;

    @Column(name = "details", definition = "json NULL COMMENT '触发上下文与计划边界详情'")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> details;

    @Column(name = "triggered_by", definition = "varchar(100) NOT NULL COMMENT '触发者'")
    private String triggeredBy;

    @Column(name = "triggered_at", definition = "datetime(3) NOT NULL COMMENT '任务创建时间 UTC'")
    private LocalDateTime triggeredAt;

    @Column(name = "scheduled_at", definition = "datetime(3) NOT NULL COMMENT '计划执行时间 UTC'")
    private LocalDateTime scheduledAt;

    @Column(name = "started_at", definition = "datetime(3) NULL COMMENT '首次开始时间 UTC'")
    private LocalDateTime startedAt;

    @Column(name = "completed_at", definition = "datetime(3) NULL COMMENT '最终完成时间 UTC'")
    private LocalDateTime completedAt;

    @Column(name = "next_retry_at", definition = "datetime(3) NULL COMMENT '下次重试时间 UTC'")
    private LocalDateTime nextRetryAt;

    @Column(name = "status", definition = "tinyint NOT NULL COMMENT '任务状态'")
    private Integer status;

    @Column(name = "attempt_count", definition = "int NOT NULL DEFAULT 0 COMMENT '已执行次数'")
    private Integer attemptCount;

    @Column(name = "max_attempts", definition = "int NOT NULL DEFAULT 3 COMMENT '最大执行次数'")
    private Integer maxAttempts;

    @Column(name = "error_message", definition = "text NULL COMMENT '最终或最近错误'")
    private String errorMessage;
}
