package com.dreamy.identity.controller;

import com.dreamy.identity.aspect.AuditLog;
import com.dreamy.identity.aspect.RequirePermission;
import com.dreamy.identity.controller.pojo.AdminAuthSessionView;
import com.dreamy.identity.controller.pojo.AdminMeView;
import com.dreamy.identity.domain.admin.service.AdminService;
import com.dreamy.identity.domain.enums.AdminStatus;
import com.dreamy.identity.dto.AdminDTO;
import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.security.AuthContext;
import com.dreamy.identity.security.AuthPrincipal;
import huihao.page.Paginated;
import huihao.web.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    public ResponseEntity<R<AdminAuthSessionView>> login(@Valid @RequestBody LoginRequest req,
                                                     jakarta.servlet.http.HttpServletRequest http) {
        AdminService.LoginOutcome outcome = adminService.login(req.email(), req.password(),
                http.getRemoteAddr(), http.getHeader("User-Agent"));
        boolean isSuper = outcome.role() != null && Boolean.TRUE.equals(outcome.role().getIsLocked());
        return ResponseEntity.ok(R.ok(new AdminAuthSessionView(
                outcome.token(), outcome.adminDTO(), outcome.permissionKeys(), isSuper)));
    }

    /** 3.2 adminLogout */
    @PostMapping("/api/admin/auth/logout")
    public ResponseEntity<R<Void>> logout() {
        adminService.logout(principal().tokenId());
        return ResponseEntity.ok(R.ok());
    }

    /** 3.3 adminMe（FUNC-021 守卫数据源） */
    @GetMapping("/api/admin/auth/me")
    public ResponseEntity<R<AdminMeView>> me() {
        AuthPrincipal p = principal();
        AdminService.MeData data = adminService.meData(Long.parseLong(p.subject()), p.tokenId());
        return ResponseEntity.ok(R.ok(new AdminMeView(
                data.admin(), data.roleName(), data.isSuper(), data.permissionKeys())));
    }

    /** 3.10 currentPermissions：实时权限查询，刷新页面即生效，无需重登 */
    @GetMapping("/api/admin/auth/permissions")
    public ResponseEntity<R<List<String>>> permissions() {
        AuthPrincipal p = principal();
        return ResponseEntity.ok(R.ok(adminService.currentPermissions(Long.parseLong(p.subject()), p.tokenId())));
    }

    /** 3.4 listAdmins（RBAC /system/admins） */
    @RequirePermission("/system/admins")
    @GetMapping("/api/admin/admins")
    public ResponseEntity<R<Paginated<AdminDTO>>> listAdmins(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int pageSize,
                                             @RequestParam(required = false) AdminStatus status,
                                             @RequestParam(required = false) Long roleId) {
        AdminService.PageData<AdminDTO> pg = adminService.pageAdminDTOs(page, pageSize, status, roleId);
        Paginated<AdminDTO> paginated = new Paginated<>();
        paginated.setData(pg.items());
        paginated.setTotalElements(pg.total());
        paginated.setPageNumber(pg.page());
        paginated.setPageSize(pg.pageSize());
        paginated.setNumberOfElements(pg.items().size());
        paginated.setTotalPages(pg.pageSize() > 0 ? (int) Math.ceil((double) pg.total() / pg.pageSize()) : 0);
        return ResponseEntity.ok(R.ok(paginated));
    }

    /** 3.5 createAdmin（FLOW-10 FUNC-015） */
    @RequirePermission("/system/admins")
    @AuditLog(action = "创建管理员")
    @PostMapping("/api/admin/admins")
    public ResponseEntity<R<AdminDTO>> createAdmin(@Valid @RequestBody CreateAdminRequest req) {
        AdminDTO admin = adminService.createAdminDTO(req.name(), req.email(), req.password(), req.roleId());
        return ResponseEntity.status(201).body(R.ok(admin));
    }

    /** 3.6 updateAdmin（FLOW-10 FUNC-016） */
    @RequirePermission("/system/admins")
    @AuditLog(action = "编辑管理员")
    @PutMapping("/api/admin/admins/{id}")
    public ResponseEntity<R<AdminDTO>> updateAdmin(@PathVariable Long id,
                                              @RequestBody UpdateAdminRequest req) {
        return ResponseEntity.ok(R.ok(adminService.updateAdminDTO(id, req.name(), req.roleId())));
    }

    /** 3.7 deleteAdmin（FLOW-10 FUNC-017） */
    @RequirePermission("/system/admins")
    @AuditLog(action = "删除管理员")
    @DeleteMapping("/api/admin/admins/{id}")
    public ResponseEntity<R<Void>> deleteAdmin(@PathVariable Long id) {
        adminService.deleteAdmin(id, Long.parseLong(principal().subject()));
        return ResponseEntity.ok(R.ok());
    }

    /** 3.8 toggleAdminStatus（EDGE-014） */
    @RequirePermission("/system/admins")
    @AuditLog(action = "禁用管理员")
    @PatchMapping("/api/admin/admins/{id}/status")
    public ResponseEntity<R<AdminDTO>> toggleStatus(@PathVariable Long id,
                                               @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(R.ok(adminService.toggleStatusDTO(id, AdminStatus.valueOf(body.get("status").toUpperCase()))));
    }

    /** 3.9 resetAdminPassword */
    @RequirePermission("/system/admins")
    @AuditLog(action = "重置密码")
    @PatchMapping("/api/admin/admins/{id}/password")
    public ResponseEntity<R<Void>> resetPassword(@PathVariable Long id,
                                              @Valid @RequestBody ResetPasswordRequest req) {
        adminService.resetPassword(id, req.newPassword());
        return ResponseEntity.ok(R.ok());
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
                                     @NotNull Long roleId) {}
    public record UpdateAdminRequest(String name, Long roleId) {}
    public record ResetPasswordRequest(@NotBlank @Size(min = 6) String newPassword) {}
}
