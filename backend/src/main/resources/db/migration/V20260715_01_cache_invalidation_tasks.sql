CREATE TABLE IF NOT EXISTS cache_invalidation_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    correlation_id VARCHAR(36) NOT NULL,
    trigger_mode VARCHAR(20) NOT NULL,
    trigger_point VARCHAR(100) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id VARCHAR(64) NULL,
    resource_label VARCHAR(255) NULL,
    targets JSON NOT NULL,
    details JSON NULL,
    triggered_by VARCHAR(100) NOT NULL,
    triggered_at DATETIME(3) NOT NULL,
    scheduled_at DATETIME(3) NOT NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    next_retry_at DATETIME(3) NULL,
    status TINYINT NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    error_message TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NULL,
    INDEX idx_cache_task_status_due (status, next_retry_at, scheduled_at),
    INDEX idx_cache_task_resource (resource_type, resource_id),
    INDEX idx_cache_task_triggered_at (triggered_at),
    INDEX idx_cache_task_correlation (correlation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='缓存失效任务';

CREATE TABLE IF NOT EXISTS cache_invalidation_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    target VARCHAR(100) NOT NULL,
    status TINYINT NOT NULL,
    attempt INT NOT NULL,
    started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    result_detail TEXT NULL,
    error_message TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NULL,
    INDEX idx_cache_step_task (task_id, id),
    INDEX idx_cache_step_status (status),
    CONSTRAINT fk_cache_step_task FOREIGN KEY (task_id) REFERENCES cache_invalidation_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='缓存失效执行步骤';

UPDATE permission
SET perm_code = '/system/cache', `group` = '系统管理', label = '缓存管理'
WHERE perm_code = '/publish'
  AND NOT EXISTS (SELECT 1 FROM (SELECT perm_code FROM permission WHERE perm_code = '/system/cache') existing_cache_permission);

-- 如果开发库里新旧权限已同时存在，将旧角色授权合并到新权限，再彻底删除 /publish。
UPDATE IGNORE role_permission rp
JOIN permission old_permission ON old_permission.id = rp.permission_id
JOIN permission new_permission ON new_permission.perm_code = '/system/cache'
SET rp.permission_id = new_permission.id,
    rp.updated_at = UTC_TIMESTAMP(3)
WHERE old_permission.perm_code = '/publish';

DELETE rp
FROM role_permission rp
JOIN permission old_permission ON old_permission.id = rp.permission_id
WHERE old_permission.perm_code = '/publish';

DELETE FROM permission WHERE perm_code = '/publish';
