package com.dreamy.controller;

import com.dreamy.domain.cache.service.AdminCacheService;
import com.dreamy.dto.CacheInvalidationLogDto;
import com.dreamy.aspect.RequirePermission;
import huihao.page.Paginated;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台缓存管理控制器（发布中心 - 缓存失效监控）。
 * 权限点：/cache（运维功能，通常限超管或运维角色）。
 */
@RestController
public class AdminCacheController {

    private static final String PERMISSION = "/cache";

    private final AdminCacheService cacheService;

    public AdminCacheController(AdminCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * 分页查询缓存失效日志。
     * GET /api/admin/cache/invalidation-logs
     * @param page 页码（从 1 开始，默认 1）
     * @param pageSize 每页大小（默认 50，最大 100）
     * @param eventType 事件类型过滤（可选）
     * @param resourceType 资源类型过滤（可选）
     * @param status 状态过滤（可选：0=pending 1=completed 2=failed）
     * @return 分页日志列表
     */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/cache/invalidation-logs")
    public ResponseEntity<R<Paginated<CacheInvalidationLogDto>>> listLogs(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "event_type", required = false) String eventType,
            @RequestParam(name = "resource_type", required = false) String resourceType,
            @RequestParam(required = false) Integer status) {
        Paginated<CacheInvalidationLogDto> result = cacheService.pageList(page, pageSize, eventType, resourceType, status);
        return ResponseEntity.ok(R.ok(result));
    }

    /**
     * 手动触发缓存失效（运维工具）。
     * POST /api/admin/cache/invalidate
     * @param request 包含 paths（路径数组）或 slug + resourceType
     * @return 触发结果
     */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/cache/invalidate")
    public ResponseEntity<R<String>> manualInvalidate(@RequestBody ManualInvalidateRequest request) {
        // TODO: 实现手动触发逻辑（调用 ContentInvalidatedPublisher 或直接调用 CDN API）
        // 这里先返回占位响应
        return ResponseEntity.ok(R.ok("手动失效功能开发中"));
    }

    /**
     * 手动失效请求载荷。
     */
    public record ManualInvalidateRequest(
            String[] paths,
            String slug,
            String resourceType
    ) {}
}
