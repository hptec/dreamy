package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.gateway.service.GatewayConfigService;
import com.dreamy.dto.GatewayDtos.GatewayConfigDto;
import com.dreamy.dto.GatewayDtos.GatewayConfigUpsert;
import com.dreamy.dto.GatewayDtos.GatewayTestResult;
import com.dreamy.error.GatewayErrorCode;
import com.dreamy.error.GatewayException;
import huihao.page.Paginated;
import huihao.web.R;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台外部网关配置控制器（7 端点，RBAC /system/gateways）。
 * L2 TRACE: i18n-backend-api-detail.md §1 / FUNC-004~007/021 / EDGE-008（无权限 40300）。
 */
@RestController
public class AdminGatewayController {

    private static final String PERMISSION = "/system/gateways";

    private final GatewayConfigService service;

    public AdminGatewayController(GatewayConfigService service) {
        this.service = service;
    }

    /** §1.1 创建（201）。 */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/gateway/configs")
    public ResponseEntity<R<GatewayConfigDto>> create(@Valid @RequestBody GatewayConfigUpsert req) {
        return ResponseEntity.status(201).body(R.ok(service.create(req)));
    }

    /** §1.2 列表分页（gateway_type 可选）。 */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/gateway/configs")
    public ResponseEntity<R<Paginated<GatewayConfigDto>>> list(
            @RequestParam(name = "gateway_type", required = false) Integer gatewayType,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ResponseEntity.ok(R.ok(service.list(gatewayType, page, pageSize)));
    }

    /** §1.3 详情。 */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/gateway/configs/{id}")
    public ResponseEntity<R<GatewayConfigDto>> get(@PathVariable String id) {
        return ResponseEntity.ok(R.ok(service.getById(parseId(id))));
    }

    /** §1.4 更新。 */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/gateway/configs/{id}")
    public ResponseEntity<R<GatewayConfigDto>> update(@PathVariable String id,
                                                      @Valid @RequestBody GatewayConfigUpsert req) {
        return ResponseEntity.ok(R.ok(service.update(parseId(id), req)));
    }

    /** §1.5 删除（204）。 */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/gateway/configs/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(parseId(id));
        return ResponseEntity.noContent().build();
    }

    /** §1.6 手动同步模型。 */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/gateway/configs/{id}/sync-models")
    public ResponseEntity<R<GatewayConfigDto>> syncModels(@PathVariable String id) {
        return ResponseEntity.ok(R.ok(service.syncModels(parseId(id))));
    }

    /** §1.7 测试连接（成功失败均 200，结果在 body.reachable）。 */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/gateway/configs/{id}/test")
    public ResponseEntity<R<GatewayTestResult>> test(@PathVariable String id) {
        return ResponseEntity.ok(R.ok(service.testConnection(parseId(id))));
    }

    /** id 非法视同不存在 → 404201。 */
    private Long parseId(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new GatewayException(GatewayErrorCode.GATEWAY_NOT_FOUND);
    }
}
