package com.dreamy.identity.admin.controller;

import com.dreamy.identity.admin.aspect.AuditLog;
import com.dreamy.identity.admin.aspect.RequirePermission;
import com.dreamy.identity.admin.dto.UserDetailView;
import com.dreamy.identity.common.domain.service.UserOpsService;
import com.dreamy.identity.common.dto.UserProfileDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户运营控制器（/api/admin/users/*）。
 * 约束: FLOW-12；FUNC-022；RBAC /customers。
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserOpsController {

    private final UserOpsService userOpsService;

    public UserOpsController(UserOpsService userOpsService) {
        this.userOpsService = userOpsService;
    }

    /** 5.1 listUsers（RM-007 分页筛选） */
    @RequirePermission("/customers")
    @GetMapping
    public ResponseEntity<Object> listUsers(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String tier,
                                            @RequestParam(required = false) String email) {
        UserOpsService.PageData<UserProfileDTO> pg =
                userOpsService.pageUserDTOs(page, pageSize, status, tier, email);
        return ResponseEntity.ok(Map.of("items", pg.items(), "total", pg.total(),
                "page", pg.page(), "page_size", pg.pageSize()));
    }

    /** 5.2 getUserDetail（NP-001 防 N+1 批量查） */
    @RequirePermission("/customers")
    @GetMapping("/{id}")
    public ResponseEntity<Object> getUserDetail(@PathVariable String id) {
        UserOpsService.UserDetailData d = userOpsService.userDetail(id, 20);
        return ResponseEntity.ok(new UserDetailView(d.user(), d.identities(), d.sessions(), d.loginHistory()));
    }

    /** 5.3 toggleUserStatus（FLOW-12 FUNC-022） */
    @RequirePermission("/customers")
    @AuditLog(action = "用户禁用")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Object> toggleStatus(@PathVariable String id,
                                               @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(userOpsService.toggleUserStatusDTO(id, body.get("status")));
    }

    /** 5.4 forceLogoutUserSessions（FLOW-12 FUNC-022 EDGE-023） */
    @RequirePermission("/customers")
    @AuditLog(action = "强制下线")
    @PostMapping("/{id}/sessions/force-logout")
    public ResponseEntity<Void> forceLogout(@PathVariable String id,
                                            @RequestBody Map<String, String> body) {
        userOpsService.forceLogout(id, body.get("scope"), body.get("session_id"));
        return ResponseEntity.noContent().build();
    }
}
