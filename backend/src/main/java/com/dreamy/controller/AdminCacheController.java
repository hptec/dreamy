package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.cache.service.AdminCacheTaskQueryService;
import com.dreamy.domain.cache.service.CacheInvalidationTarget;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.dto.CacheInvalidationTaskDto;
import huihao.page.Paginated;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
public class AdminCacheController {

    private static final String PERMISSION = "/system/cache";

    private final AdminCacheTaskQueryService queryService;
    private final CacheInvalidationTaskService taskService;

    public AdminCacheController(AdminCacheTaskQueryService queryService, CacheInvalidationTaskService taskService) {
        this.queryService = queryService;
        this.taskService = taskService;
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/cache/tasks")
    public ResponseEntity<R<Paginated<CacheInvalidationTaskDto>>> listTasks(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "trigger_mode", required = false) String triggerMode,
            @RequestParam(name = "resource_type", required = false) String resourceType,
            @RequestParam(required = false) Integer status) {
        return ResponseEntity.ok(R.ok(queryService.pageList(page, pageSize, triggerMode, resourceType, status)));
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/cache/summary")
    public ResponseEntity<R<Map<String, Long>>> summary() {
        return ResponseEntity.ok(R.ok(queryService.summary()));
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/cache/targets")
    public ResponseEntity<R<List<String>>> targets() {
        return ResponseEntity.ok(R.ok(Arrays.stream(CacheInvalidationTarget.values()).map(Enum::name).toList()));
    }

    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/cache/tasks")
    public ResponseEntity<R<ManualTaskResponse>> createManualTask(@RequestBody ManualTaskRequest request) {
        if (request == null || request.targets() == null || request.targets().isEmpty()) {
            return ResponseEntity.badRequest().body(R.fail(400, "至少选择一个缓存目标"));
        }
        String reason = request.reason() == null ? null : request.reason().trim();
        if (reason != null && reason.length() > 255) {
            return ResponseEntity.badRequest().body(R.fail(400, "清理原因不能超过 255 个字符"));
        }
        if (request.targets().stream().anyMatch(value -> value == null || value.isBlank())) {
            return ResponseEntity.badRequest().body(R.fail(400, "包含不支持的缓存目标"));
        }
        List<CacheInvalidationTarget> targets;
        try {
            targets = request.targets().stream().distinct().map(CacheInvalidationTarget::valueOf).toList();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(R.fail(400, "包含不支持的缓存目标"));
        }
        Long taskId = taskService.enqueue(CacheInvalidationTaskService.MODE_MANUAL,
                "manual.invalidate", "manual", null,
                reason == null || reason.isBlank() ? "手动清理" : reason, targets,
                null, Map.of("reason", reason == null ? "" : reason), null);
        return ResponseEntity.ok(R.ok(new ManualTaskResponse(taskId, targets.size())));
    }

    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/cache/tasks/{id}/retry")
    public ResponseEntity<R<Map<String, Boolean>>> retry(@PathVariable Long id) {
        try {
            taskService.retry(id);
            return ResponseEntity.ok(R.ok(Map.of("accepted", true)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(R.fail(404, "缓存任务不存在"));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(R.fail(409, "只有失败或部分失败任务可以重试"));
        }
    }

    public record ManualTaskRequest(List<String> targets, String reason) {}
    public record ManualTaskResponse(Long taskId, int targetCount) {}
}
