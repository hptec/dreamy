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

/**
 * 缓存失效日志表（追踪 CDN 缓存清除事件）。
 * 记录所有通过 ContentInvalidatedPublisher 触发的缓存失效事件，
 * 供「发布中心」监控页面展示和手动触发失效使用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cache_invalidation_log", comment = "缓存失效日志", indexes = {
        @Index(name = "idx_event_type", columns = {"event_type"}),
        @Index(name = "idx_resource", columns = {"resource_type", "resource_id"}),
        @Index(name = "idx_slug", columns = {"slug"}),
        @Index(name = "idx_triggered_at", columns = {"triggered_at"}),
        @Index(name = "idx_status", columns = {"status"})
})
@TableName(value = "cache_invalidation_log", autoResultMap = true)
public class CacheInvalidationLog extends LongAuditableEntity {

    @Column(name = "event_type", definition = "varchar(50) NOT NULL COMMENT '事件类型'")
    private String eventType;

    @Column(name = "resource_type", definition = "varchar(20) NOT NULL COMMENT '资源类型'")
    private String resourceType;

    @Column(name = "resource_id", definition = "bigint NULL COMMENT '资源 ID'")
    private Long resourceId;

    @Column(name = "slug", definition = "varchar(255) NULL COMMENT '资源 slug'")
    private String slug;

    @Column(name = "old_slug", definition = "varchar(255) NULL COMMENT '旧 slug（变更时双失效）'")
    private String oldSlug;

    @Column(name = "affected_paths", definition = "json NULL COMMENT '受影响的路径列表'")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> affectedPaths;

    @Column(name = "locales", definition = "json NULL COMMENT '受影响的语言'")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> locales;

    @Column(name = "triggered_by", definition = "varchar(100) NULL COMMENT '触发者'")
    private String triggeredBy;

    @Column(name = "triggered_at", definition = "datetime(3) NOT NULL COMMENT '触发时间'")
    private LocalDateTime triggeredAt;

    @Column(name = "status", definition = "tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0=pending 1=completed 2=failed'")
    private Integer status;

    @Column(name = "completed_at", definition = "datetime(3) NULL COMMENT '完成时间'")
    private LocalDateTime completedAt;

    @Column(name = "error_message", definition = "text NULL COMMENT '失败错误信息'")
    private String errorMessage;

    @Column(name = "created_at", definition = "datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间'")
    private LocalDateTime createdAt;

    // 状态常量
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_COMPLETED = 1;
    public static final int STATUS_FAILED = 2;
}
