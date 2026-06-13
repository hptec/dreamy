-- 缓存失效日志表（追踪 CDN 缓存清除事件）
-- TASK: 方案 B 缓存失效监控面板

CREATE TABLE cache_invalidation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志 ID',
    event_type VARCHAR(50) NOT NULL COMMENT '事件类型: product_created/product_updated/product_status_changed/blog_changed 等',
    resource_type VARCHAR(20) NOT NULL COMMENT '资源类型: product/blog/wedding/lookbook/guide/banner/category/tag',
    resource_id BIGINT COMMENT '资源 ID（部分类型有，如 wedding/banner）',
    slug VARCHAR(255) COMMENT '资源 slug（部分类型有）',
    old_slug VARCHAR(255) COMMENT '旧 slug（slug 变更时记录，需要双失效）',

    affected_paths JSON COMMENT '受影响的路径列表 ["/product/xxx", "/en/product/xxx", ...]',
    locales JSON COMMENT '受影响的语言 ["en","es","fr"]',

    triggered_by VARCHAR(100) COMMENT '触发者：system/admin:{adminId}/scheduler',
    triggered_at DATETIME(3) NOT NULL COMMENT '触发时间（微秒精度）',

    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0=pending 1=completed 2=failed',
    completed_at DATETIME(3) COMMENT '完成时间',
    error_message TEXT COMMENT '失败时的错误信息',

    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',

    INDEX idx_event_type (event_type),
    INDEX idx_resource (resource_type, resource_id),
    INDEX idx_slug (slug),
    INDEX idx_triggered_at (triggered_at DESC),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='缓存失效日志';
