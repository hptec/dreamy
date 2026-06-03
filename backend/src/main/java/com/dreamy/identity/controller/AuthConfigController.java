package com.dreamy.identity.controller;

import com.dreamy.identity.aspect.AuditLog;
import com.dreamy.identity.aspect.RequirePermission;
import com.dreamy.identity.domain.audit.service.AuditService;
import com.dreamy.identity.domain.authconfig.service.AuthConfigService;
import com.dreamy.identity.dto.AuthConfigUpdateRequest;
import com.dreamy.identity.dto.AuthConfigView;
import com.dreamy.identity.dto.OperationLogDTO;
import com.dreamy.identity.dto.mapper.IdentityDtoMapper;
import huihao.page.Paginated;
import huihao.web.R;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证配置 + 操作日志控制器（/api/admin/auth-config + /api/admin/operation-logs）。
 * 约束: FLOW-13；FUNC-023/024；EDGE-018/019；RBAC /system/auth + /system/logs。
 */
@RestController
public class AuthConfigController {

    private final AuthConfigService authConfigService;
    private final AuditService auditService;
    private final IdentityDtoMapper mapper;

    public AuthConfigController(AuthConfigService authConfigService, AuditService auditService,
                                IdentityDtoMapper mapper) {
        this.authConfigService = authConfigService;
        this.auditService = auditService;
        this.mapper = mapper;
    }

    /** 6.1 getAuthConfig（AuthSettings 页）：返回 DTO，不暴露 Repository Entity */
    @RequirePermission("/system/auth")
    @GetMapping("/api/admin/auth-config")
    public ResponseEntity<R<AuthConfigView>> getAuthConfig() {
        return ResponseEntity.ok(R.ok(authConfigService.getConfigView()));
    }

    /** 6.2 updateAuthConfig（FLOW-13 FUNC-023 EDGE-019）：CV-002 区间校验 + @CacheInvalidate store:authconfig */
    @RequirePermission("/system/auth")
    @AuditLog(action = "认证配置变更", target = "认证配置")
    @PutMapping("/api/admin/auth-config")
    public ResponseEntity<R<AuthConfigView>> updateAuthConfig(@RequestBody AuthConfigUpdateRequest update) {
        return ResponseEntity.ok(R.ok(authConfigService.updateConfig(update)));
    }

    /** 6.3 listOperationLogs（FUNC-024 EDGE-018）：分页倒序，只读无 delete */
    @RequirePermission("/system/logs")
    @GetMapping("/api/admin/operation-logs")
    public ResponseEntity<R<Paginated<OperationLogDTO>>> listOperationLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        var pg = auditService.page(page, pageSize, action, operatorId, from, to);
        List<OperationLogDTO> items = pg.getRecords().stream().map(mapper::toOperationLog).toList();
        long total = pg.getTotal();
        Paginated<OperationLogDTO> paginated = new Paginated<>();
        paginated.setData(items);
        paginated.setTotalElements(total);
        paginated.setPageNumber(page);
        paginated.setPageSize(pageSize);
        paginated.setNumberOfElements(items.size());
        paginated.setTotalPages(pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0);
        return ResponseEntity.ok(R.ok(paginated));
    }

    /** 6.4 exportOperationLogs（CSV 流式，RM-102） */
    @RequirePermission("/system/logs")
    @GetMapping("/api/admin/operation-logs/export")
    public void exportOperationLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            HttpServletResponse response) throws IOException {
        // 时间窗校验必须先于写出任何响应头/表头：校验失败时响应尚未提交，
        // GlobalExceptionHandler 才能正常返回 JSON 错误体。否则 Content-Type 已锁为
        // text/csv，写 R 包络会触发 HttpMessageNotWritableException。
        auditService.validateExportWindow(from, to);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=operation-logs.csv");
        PrintWriter writer = response.getWriter();
        writer.println("id,operator_name,action,target,ip,created_at");
        // BLOCKER-5：流式逐行写出（ResultHandler 回调），不全量物化进堆；时间窗上限由 AuditService 强制
        auditService.streamForExport(action, operatorId, from, to, log -> {
            writer.printf("%s,%s,%s,%s,%s,%s%n",
                    csv(String.valueOf(log.getId())), csv(log.getOperatorName()), csv(log.getAction()),
                    csv(log.getTarget()), csv(log.getIp()), log.getCreatedAt());
        });
    }

    /**
     * EDGE-018：操作日志只读，不可删除。显式拒绝任意 DELETE，返回 405 METHOD_NOT_ALLOWED，
     * 而非落入无路由 catch-all 的 500。审计完整性要求。
     */
    @DeleteMapping({"/api/admin/operation-logs", "/api/admin/operation-logs/{id}"})
    public ResponseEntity<R<Void>> deleteOperationLogForbidden(@PathVariable(required = false) Long id) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED)
                .body(new R<>(40500, "操作日志只读，不可删除", null));
    }

    private String csv(String v) {
        if (v == null) return "";
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
