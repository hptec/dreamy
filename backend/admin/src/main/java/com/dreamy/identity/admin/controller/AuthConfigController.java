package com.dreamy.identity.admin.controller;

import com.dreamy.identity.admin.aspect.AuditLog;
import com.dreamy.identity.admin.aspect.RequirePermission;
import com.dreamy.identity.common.domain.service.AuditService;
import com.dreamy.identity.common.domain.service.AuthConfigService;
import com.dreamy.identity.common.dto.AuthConfigUpdateRequest;
import com.dreamy.identity.common.dto.AuthConfigView;
import com.dreamy.identity.common.dto.OperationLogDTO;
import com.dreamy.identity.common.dto.mapper.IdentityDtoMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

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
    public ResponseEntity<AuthConfigView> getAuthConfig() {
        return ResponseEntity.ok(authConfigService.getConfigView());
    }

    /** 6.2 updateAuthConfig（FLOW-13 FUNC-023 EDGE-019）：CV-002 区间校验 + @CacheInvalidate store:authconfig */
    @RequirePermission("/system/auth")
    @AuditLog(action = "认证配置变更")
    @PutMapping("/api/admin/auth-config")
    public ResponseEntity<AuthConfigView> updateAuthConfig(@RequestBody AuthConfigUpdateRequest update) {
        return ResponseEntity.ok(authConfigService.updateConfig(update));
    }

    /** 6.3 listOperationLogs（FUNC-024 EDGE-018）：分页倒序，只读无 delete */
    @RequirePermission("/system/logs")
    @GetMapping("/api/admin/operation-logs")
    public ResponseEntity<Object> listOperationLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String operatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        var pg = auditService.page(page, pageSize, action, operatorId, from, to);
        List<OperationLogDTO> items = pg.getRecords().stream().map(mapper::toOperationLog).toList();
        return ResponseEntity.ok(Map.of("items", items, "total", pg.getTotal(),
                "page", page, "page_size", pageSize));
    }

    /** 6.4 exportOperationLogs（CSV 流式，RM-102） */
    @RequirePermission("/system/logs")
    @GetMapping("/api/admin/operation-logs/export")
    public void exportOperationLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String operatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=operation-logs.csv");
        PrintWriter writer = response.getWriter();
        writer.println("id,operator_name,action,target,ip,created_at");
        // BLOCKER-5：流式逐行写出（ResultHandler 回调），不全量物化进堆；时间窗上限由 AuditService 强制
        auditService.streamForExport(action, operatorId, from, to, log -> {
            writer.printf("%s,%s,%s,%s,%s,%s%n",
                    csv(log.getId()), csv(log.getOperatorName()), csv(log.getAction()),
                    csv(log.getTarget()), csv(log.getIp()), log.getCreatedAt());
        });
    }

    /**
     * EDGE-018：操作日志只读，不可删除。显式拒绝任意 DELETE，返回 405 METHOD_NOT_ALLOWED，
     * 而非落入无路由 catch-all 的 500。审计完整性要求。
     */
    @DeleteMapping({"/api/admin/operation-logs", "/api/admin/operation-logs/{id}"})
    public ResponseEntity<Object> deleteOperationLogForbidden(@PathVariable(required = false) String id) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED)
                .body(java.util.Map.of("code", 40500, "message", "操作日志只读，不可删除", "details",
                        java.util.Map.of()));
    }

    private String csv(String v) {
        if (v == null) return "";
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
