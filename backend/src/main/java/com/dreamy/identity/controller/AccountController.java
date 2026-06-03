package com.dreamy.identity.controller;

import com.dreamy.identity.domain.user.model.LoginContext;
import com.dreamy.identity.domain.authconfig.service.AuthConfigService;
import com.dreamy.identity.domain.user.service.IdentityService;
import com.dreamy.identity.domain.session.service.SessionService;
import com.dreamy.identity.dto.IdentityDTO;
import com.dreamy.identity.dto.SessionDTO;
import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.security.AuthContext;
import com.dreamy.identity.security.AuthPrincipal;
import huihao.web.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Store 账户安全控制器（/api/store/account/*，需 store JWT）。
 * 约束: FLOW-05~08；FUNC-007~013/026/027；EDGE-007~010/020/021。
 */
@RestController
@RequestMapping("/api/store/account")
public class AccountController {

    private final IdentityService identityService;
    private final SessionService sessionService;

    public AccountController(IdentityService identityService, SessionService sessionService) {
        this.identityService = identityService;
        this.sessionService = sessionService;
    }

    /** 2.1 getProfile（FUNC-007） */
    @GetMapping("/profile")
    public ResponseEntity<R<com.dreamy.identity.dto.UserProfileDTO>> getProfile() {
        return ResponseEntity.ok(R.ok(identityService.getProfileView(userId())));
    }

    /** 2.2 listIdentities（FUNC-010） */
    @GetMapping("/identities")
    public ResponseEntity<R<List<IdentityDTO>>> listIdentities() {
        List<IdentityDTO> items = identityService.listIdentityViews(userId());
        return ResponseEntity.ok(R.ok(items));
    }

    /** 2.3 bindIdentity（FLOW-05 FUNC-008） */
    @PostMapping("/identities/bind")
    public ResponseEntity<R<List<IdentityDTO>>> bindIdentity(@Valid @RequestBody BindRequest req,
                                                            jakarta.servlet.http.HttpServletRequest http) {
        List<IdentityDTO> items = identityService.bindIdentityViews(
                userId(), req.provider(), req.idToken(), req.email(), req.code(), http.getRemoteAddr());
        return ResponseEntity.ok(R.ok(items));
    }

    /** 2.4 unbindIdentity（FLOW-05 FUNC-009 R2） */
    @DeleteMapping("/identities/{identityId}")
    public ResponseEntity<R<Void>> unbindIdentity(@PathVariable Long identityId) {
        identityService.unbindIdentity(userId(), identityId);
        return ResponseEntity.ok(R.ok());
    }

    /** 2.5 changePrimaryEmail（FLOW-06 FUNC-026） */
    @PostMapping("/email/change-primary")
    public ResponseEntity<R<List<IdentityDTO>>> changePrimaryEmail(@Valid @RequestBody ChangePrimaryRequest req) {
        List<IdentityDTO> items = identityService.changePrimaryEmailViews(
                userId(), req.newEmail(), req.code());
        return ResponseEntity.ok(R.ok(items));
    }

    /** 2.6 listSessions（FUNC-011/013） */
    @GetMapping("/sessions")
    public ResponseEntity<R<List<SessionDTO>>> listSessions() {
        AuthPrincipal p = principal();
        List<SessionDTO> items = sessionService.listActiveViews(Long.parseLong(p.subject()), p.tokenId());
        return ResponseEntity.ok(R.ok(items));
    }

    /** 2.7 revokeSession（FLOW-07 FUNC-012）：归属校验下沉 Service（EDGE-009） */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<R<Void>> revokeSession(@PathVariable Long sessionId) {
        sessionService.revoke(userId(), sessionId);
        return ResponseEntity.ok(R.ok());
    }

    /** 2.8 revokeOtherSessions（FLOW-07 FUNC-012） */
    @DeleteMapping("/sessions/others")
    public ResponseEntity<R<Void>> revokeOtherSessions() {
        AuthPrincipal p = principal();
        sessionService.revokeAllExcept(Long.parseLong(p.subject()), p.tokenId());
        return ResponseEntity.ok(R.ok());
    }

    /** 2.9 deleteAccount（FLOW-08 FUNC-027） */
    @PostMapping("/delete")
    public ResponseEntity<R<Void>> deleteAccount(@Valid @RequestBody DeleteAccountRequest req) {
        if (!Boolean.TRUE.equals(req.confirm())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        identityService.deleteAccount(userId());
        return ResponseEntity.ok(R.ok());
    }

    private AuthPrincipal principal() {
        AuthPrincipal p = AuthContext.get();
        if (p == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return p;
    }

    /** JWT sub（store=user_id 字符串）转 Long 主键，供领域服务调用（契约 §6 边界转换） */
    private Long userId() {
        return Long.parseLong(principal().subject());
    }

    public record BindRequest(String provider, String idToken, String email, String code) {}
    public record ChangePrimaryRequest(@NotBlank String newEmail, @NotBlank String code) {}
    public record DeleteAccountRequest(@NotNull Boolean confirm) {}
}
