package com.dreamy.domain.cache.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** One observable target execution belonging to a cache invalidation task. */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "cache_invalidation_step", comment = "缓存失效执行步骤", indexes = {
        @Index(name = "idx_cache_step_task", columns = {"task_id", "id"}),
        @Index(name = "idx_cache_step_status", columns = {"status"})
})
@TableName("cache_invalidation_step")
public class CacheInvalidationStep extends LongAuditableEntity {

    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_SUCCEEDED = 1;
    public static final int STATUS_FAILED = 2;
    public static final int STATUS_SKIPPED = 3;

    @Column(name = "task_id", definition = "bigint NOT NULL COMMENT '缓存失效任务 ID'")
    private Long taskId;

    @Column(name = "step_type", definition = "varchar(32) NOT NULL COMMENT 'LOCAL_CACHE/VERIFY'")
    private String stepType;

    @Column(name = "target", definition = "varchar(100) NOT NULL COMMENT '缓存目标'")
    private String target;

    @Column(name = "status", definition = "tinyint NOT NULL COMMENT '步骤状态'")
    private Integer status;

    @Column(name = "attempt", definition = "int NOT NULL COMMENT '所属执行轮次'")
    private Integer attempt;

    @Column(name = "started_at", definition = "datetime(3) NOT NULL COMMENT '开始时间 UTC'")
    private LocalDateTime startedAt;

    @Column(name = "completed_at", definition = "datetime(3) NULL COMMENT '结束时间 UTC'")
    private LocalDateTime completedAt;

    @Column(name = "result_detail", definition = "text NULL COMMENT '代际号等执行结果'")
    private String resultDetail;

    @Column(name = "error_message", definition = "text NULL COMMENT '失败原因'")
    private String errorMessage;
}
