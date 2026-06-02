package com.dreamy.identity.admin.controller;

import com.dreamy.identity.admin.aspect.AuditLog;
import com.dreamy.identity.admin.aspect.RequirePermission;
import com.dreamy.identity.admin.dto.AdminAuthSessionView;
import com.dreamy.identity.admin.dto.AdminMeView;
import com.dreamy.identity.common.domain.service.AdminService;
import com.dreamy.identity.common.dto.AdminDTO;
import com.dreamy.identity.common.error.BizException;
import com.dreamy.identity.common.error.ErrorCode;
import com.dreamy.identity.common.security.AuthContext;
import com.dreamy.identity.common.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin 认证 + 管理员 CRUD 控制器（/api/admin/auth/* + /api/admin/admins/*）。
 * 约束: FLOW-09/10；FUNC-014~017；EDGE-011~014；RBAC /system/admins。
 */
@RestController
public class AdminAuthController {

    private final AdminService adminService;

    public AdminAuthController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** 3.1 adminLogin（FLOW-09 FUNC-014） */
    @PostMapping("/api/admin/auth/login")
    public ResponseEntity<AdminAuthSessionView> login(@Valid @RequestBody LoginRequest req,
                                                     jakarta.servlet.http.HttpServletRequest http) {
        AdminService.LoginOutcome outcome = adminService.login(req.email(), req.password(),
                http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.ok(new AdminAuthSessionView(
                outcome.token(), outcome.adminDTO(), outcome.permissionKeys()));
    }

    /** 3.2 adminLogout */
    @PostMapping("/api/admin/auth/logout")
    public ResponseEntity<Void> logout() {
        adminService.logout(principal().tokenId());
        return ResponseEntity.noContent().build();
    }

    /** 3.3 adminMe（FUNC-021 守卫数据源） */
    @GetMapping("/api/admin/auth/me")
    public ResponseEntity<AdminMeView> me() {
        AuthPrincipal p = principal();
        AdminService.MeData data = adminService.meData(p.subject(), p.tokenId());
        return ResponseEntity.ok(new AdminMeView(
                data.admin(), data.roleName(), data.isSuper(),
                p.permissionKeys() != null ? p.permissionKeys() : List.of()));
    }

    /** 3.4 listAdmins（RBAC /system/admins） */
    @RequirePermission("/system/admins")
    @GetMapping("/api/admin/admins")
    public ResponseEntity<Object> listAdmins(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int pageSize,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String roleId) {
        AdminService.PageData<AdminDTO> pg = adminService.pageAdminDTOs(page, pageSize, status, roleId);
        return ResponseEntity.ok(Map.of("items", pg.items(), "total", pg.total(),
                "page", pg.page(), "page_size", pg.pageSize()));
    }

    /** 3.5 createAdmin（FLOW-10 FUNC-015） */
    @RequirePermission("/system/admins")
    @AuditLog(action = "创建管理员")
    @PostMapping("/api/admin/admins")
    public ResponseEntity<Object> createAdmin(@Valid @RequestBody CreateAdminRequest req) {
        AdminDTO admin = adminService.createAdminDTO(req.name(), req.email(), req.password(), req.roleId());
        return ResponseEntity.status(201).body(admin);
    }

    /** 3.6 updateAdmin（FLOW-10 FUNC-016） */
    @RequirePermission("/system/admins")
    @AuditLog(action = "编辑管理员")
    @PutMapping("/api/admin/admins/{id}")
    public ResponseEntity<Object> updateAdmin(@PathVariable String id,
                                              @RequestBody UpdateAdminRequest req) {
        return ResponseEntity.ok(adminService.updateAdminDTO(id, req.name(), req.roleId()));
    }

    /** 3.7 deleteAdmin（FLOW-10 FUNC-017） */
    @RequirePermission("/system/admins")
    @AuditLog(action = "删除管理员")
    @DeleteMapping("/api/admin/admins/{id}")
    public ResponseEntity<Void> deleteAdmin(@PathVariable String id) {
        adminService.deleteAdmin(id, principal().subject());
        return ResponseEntity.noContent().build();
    }

    /** 3.8 toggleAdminStatus（EDGE-014） */
    @RequirePermission("/system/admins")
    @AuditLog(action = "禁用管理员")
    @PatchMapping("/api/admin/admins/{id}/status")
    public ResponseEntity<Object> toggleStatus(@PathVariable String id,
                                               @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(adminService.toggleStatusDTO(id, body.get("status")));
    }

    /** 3.9 resetAdminPassword */
    @RequirePermission("/system/admins")
    @AuditLog(action = "重置密码")
    @PatchMapping("/api/admin/admins/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable String id,
                                              @Valid @RequestBody ResetPasswordRequest req) {
        adminService.resetPassword(id, req.newPassword());
        return ResponseEntity.noContent().build();
    }

    private AuthPrincipal principal() {
        AuthPrincipal p = AuthContext.get();
        if (p == null) throw new BizException(ErrorCode.UNAUTHORIZED);
        return p;
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password, String redirect) {}
    public record CreateAdminRequest(@NotBlank @Size(max = 80) String name,
                                     @NotBlank @Email String email,
                                     @NotBlank @Size(min = 6) String password,
                                     @NotBlank String roleId) {}
    public record UpdateAdminRequest(String name, String roleId) {}
    public record ResetPasswordRequest(@NotBlank @Size(min = 6) String newPassword) {}
}
