package com.dreamy.identity.controller;

import com.dreamy.identity.aspect.AuditLog;
import com.dreamy.identity.aspect.RequirePermission;
import com.dreamy.identity.controller.pojo.UserDetailView;
import com.dreamy.identity.domain.user.service.UserOpsService;
import com.dreamy.identity.dto.UserProfileDTO;
import huihao.page.Paginated;
import huihao.web.R;
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
    public ResponseEntity<R<Paginated<UserProfileDTO>>> listUsers(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String tier,
                                            @RequestParam(required = false) String email) {
        UserOpsService.PageData<UserProfileDTO> pg =
                userOpsService.pageUserDTOs(page, pageSize, status, tier, email);
        Paginated<UserProfileDTO> paginated = new Paginated<>();
        paginated.setData(pg.items());
        paginated.setTotalElements(pg.total());
        paginated.setPageNumber(pg.page());
        paginated.setPageSize(pg.pageSize());
        paginated.setNumberOfElements(pg.items().size());
        paginated.setTotalPages(pg.pageSize() > 0 ? (int) Math.ceil((double) pg.total() / pg.pageSize()) : 0);
        return ResponseEntity.ok(R.ok(paginated));
    }

    /** 5.2 getUserDetail（NP-001 防 N+1 批量查） */
    @RequirePermission("/customers")
    @GetMapping("/{id}")
    public ResponseEntity<R<UserDetailView>> getUserDetail(@PathVariable Long id) {
        UserOpsService.UserDetailData d = userOpsService.userDetail(id, 20);
        return ResponseEntity.ok(R.ok(new UserDetailView(d.user(), d.identities(), d.sessions(), d.loginHistory())));
    }

    /** 5.3 toggleUserStatus（FLOW-12 FUNC-022） */
    @RequirePermission("/customers")
    @AuditLog(action = "用户禁用")
    @PatchMapping("/{id}/status")
    public ResponseEntity<R<UserProfileDTO>> toggleStatus(@PathVariable Long id,
                                               @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(R.ok(userOpsService.toggleUserStatusDTO(id, body.get("status"))));
    }

    /** 5.4 forceLogoutUserSessions（FLOW-12 FUNC-022 EDGE-023） */
    @RequirePermission("/customers")
    @AuditLog(action = "强制下线")
    @PostMapping("/{id}/sessions/force-logout")
    public ResponseEntity<R<Void>> forceLogout(@PathVariable Long id,
                                            @RequestBody Map<String, String> body) {
        String sessionIdRaw = body.get("session_id");
        Long sessionId = sessionIdRaw != null && !sessionIdRaw.isBlank() ? Long.parseLong(sessionIdRaw) : null;
        userOpsService.forceLogout(id, body.get("scope"), sessionId);
        return ResponseEntity.ok(R.ok());
    }
}
