package com.dreamy.identity.admin.controller;

import com.dreamy.identity.admin.aspect.AuditLog;
import com.dreamy.identity.admin.aspect.RequirePermission;
import com.dreamy.identity.common.domain.service.RoleService;
import com.dreamy.identity.common.dto.PermissionDTO;
import com.dreamy.identity.common.dto.RoleDTO;
import com.dreamy.identity.common.dto.mapper.IdentityDtoMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色权限控制器（/api/admin/roles + /api/admin/permissions）。
 * 约束: FLOW-11；FUNC-018~020；EDGE-015；RBAC /system/roles。
 */
@RestController
public class RoleController {

    private final RoleService roleService;
    private final IdentityDtoMapper mapper;

    public RoleController(RoleService roleService, IdentityDtoMapper mapper) {
        this.roleService = roleService;
        this.mapper = mapper;
    }

    /** 4.1 listRoles */
    @GetMapping("/api/admin/roles")
    public ResponseEntity<Map<String, Object>> listRoles() {
        List<RoleDTO> items = roleService.listRoleDTOs();
        return ResponseEntity.ok(Map.of("items", items));
    }

    /** 4.2 createRole（FUNC-018） */
    @RequirePermission("/system/roles")
    @AuditLog(action = "创建角色")
    @PostMapping("/api/admin/roles")
    public ResponseEntity<Object> createRole(@Valid @RequestBody CreateRoleRequest req) {
        return ResponseEntity.status(201).body(roleService.createRoleDTO(req.name()));
    }

    /** 4.3 updateRole（FLOW-11 FUNC-018/019） */
    @RequirePermission("/system/roles")
    @AuditLog(action = "权限变更")
    @PutMapping("/api/admin/roles/{id}")
    public ResponseEntity<Object> updateRole(@PathVariable String id,
                                             @RequestBody UpdateRoleRequest req) {
        return ResponseEntity.ok(roleService.updateRoleDTO(id, req.name(), req.permissionKeys()));
    }

    /** 4.4 deleteRole（FLOW-11 FUNC-020） */
    @RequirePermission("/system/roles")
    @AuditLog(action = "删除角色")
    @DeleteMapping("/api/admin/roles/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable String id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    /** 4.5 listPermissions（RM-080 22 项） */
    @GetMapping("/api/admin/permissions")
    public ResponseEntity<Map<String, Object>> listPermissions() {
        List<PermissionDTO> items = roleService.listPermissions().stream()
                .map(mapper::toPermission).toList();
        return ResponseEntity.ok(Map.of("items", items));
    }

    public record CreateRoleRequest(@NotBlank @Size(max = 40) String name) {}
    public record UpdateRoleRequest(String name, List<String> permissionKeys) {}
}
