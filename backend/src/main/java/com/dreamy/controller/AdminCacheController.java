package com.dreamy.controller;

import com.dreamy.domain.cache.entity.CacheInvalidationLog;
import com.dreamy.domain.cache.service.AdminCacheService;
import com.dreamy.dto.CacheInvalidationLogDto;
import com.dreamy.aspect.RequirePermission;
import com.dreamy.infra.CdnInvalidationService;
import com.dreamy.security.AuthContext;
import com.dreamy.security.AuthPrincipal;
import huihao.page.Paginated;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 后台缓存管理控制器（发布中心 - 缓存失效监控）。
 * 权限点：/publish（与前端路由一致，permission 表 id=18）。
 */
@RestController
public class AdminCacheController {

    private static final String PERMISSION = "/publish";

    private final AdminCacheService cacheService;
    private final CdnInvalidationService cdnService;

    public AdminCacheController(AdminCacheService cacheService, CdnInvalidationService cdnService) {
        this.cacheService = cacheService;
        this.cdnService = cdnService;
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
     * @return 触发结果，包含新建日志 ID
     */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/cache/invalidate")
    public ResponseEntity<R<ManualInvalidateResponse>> manualInvalidate(@RequestBody ManualInvalidateRequest request) {
        AuthPrincipal principal = AuthContext.get();
        String triggeredBy = principal != null ? "admin:" + principal.subject() : "admin";

        List<String> paths = new ArrayList<>();
        if (request.paths() != null && request.paths().length > 0) {
            for (String p : request.paths()) {
                if (p != null && !p.isBlank()) {
                    paths.add(p);
                }
            }
        } else if (request.slug() != null && request.resourceType() != null) {
            // 根据 slug + resourceType 推测路径
            String slug = request.slug();
            for (String locale : List.of("en", "es", "fr")) {
                String prefix = "en".equals(locale) ? "" : "/" + locale;
                paths.add(prefix + "/" + request.resourceType() + "/" + slug);
            }
        }

        if (paths.isEmpty()) {
            return ResponseEntity.badRequest().body(R.fail(400, "paths 或 slug+resourceType 至少提供一项"));
        }

        Long logId = cacheService.logInvalidation(
                "manual_invalidate",
                request.resourceType() != null ? request.resourceType() : "manual",
                null,
                request.slug(),
                null,
                List.of("en", "es", "fr"),
                triggeredBy,
                paths
        );

        // 异步触发 CDN 清除并回写状态
        cdnService.invalidatePaths(paths, logId);

        return ResponseEntity.ok(R.ok(new ManualInvalidateResponse(logId, paths.size())));
    }

    /**
     * 手动失效请求载荷。
     */
    public record ManualInvalidateRequest(
            String[] paths,
            String slug,
            String resourceType
    ) {}

    /**
     * 手动失效响应。
     */
    public record ManualInvalidateResponse(Long logId, int pathCount) {}
}
