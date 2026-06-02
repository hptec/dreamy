package com.dreamy.identity.store.controller;

import com.dreamy.identity.common.domain.model.LoginContext;
import com.dreamy.identity.common.domain.service.AuthConfigService;
import com.dreamy.identity.common.domain.service.IdentityService;
import com.dreamy.identity.common.domain.service.SessionService;
import com.dreamy.identity.common.dto.IdentityDTO;
import com.dreamy.identity.common.dto.SessionDTO;
import com.dreamy.identity.common.error.BizException;
import com.dreamy.identity.common.error.ErrorCode;
import com.dreamy.identity.common.security.AuthContext;
import com.dreamy.identity.common.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    public ResponseEntity<Object> getProfile() {
        return ResponseEntity.ok(identityService.getProfileView(principal().subject()));
    }

    /** 2.2 listIdentities（FUNC-010） */
    @GetMapping("/identities")
    public ResponseEntity<Map<String, Object>> listIdentities() {
        List<IdentityDTO> items = identityService.listIdentityViews(principal().subject());
        return ResponseEntity.ok(Map.of("items", items));
    }

    /** 2.3 bindIdentity（FLOW-05 FUNC-008） */
    @PostMapping("/identities/bind")
    public ResponseEntity<Map<String, Object>> bindIdentity(@Valid @RequestBody BindRequest req,
                                                            jakarta.servlet.http.HttpServletRequest http) {
        List<IdentityDTO> items = identityService.bindIdentityViews(
                principal().subject(), req.provider(), req.idToken(), req.email(), req.code(), http.getRemoteAddr());
        return ResponseEntity.ok(Map.of("items", items));
    }

    /** 2.4 unbindIdentity（FLOW-05 FUNC-009 R2） */
    @DeleteMapping("/identities/{identityId}")
    public ResponseEntity<Void> unbindIdentity(@PathVariable String identityId) {
        identityService.unbindIdentity(principal().subject(), identityId);
        return ResponseEntity.noContent().build();
    }

    /** 2.5 changePrimaryEmail（FLOW-06 FUNC-026） */
    @PostMapping("/email/change-primary")
    public ResponseEntity<Map<String, Object>> changePrimaryEmail(@Valid @RequestBody ChangePrimaryRequest req) {
        List<IdentityDTO> items = identityService.changePrimaryEmailViews(
                principal().subject(), req.newEmail(), req.code());
        return ResponseEntity.ok(Map.of("items", items));
    }

    /** 2.6 listSessions（FUNC-011/013） */
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> listSessions() {
        AuthPrincipal p = principal();
        List<SessionDTO> items = sessionService.listActiveViews(p.subject(), p.tokenId());
        return ResponseEntity.ok(Map.of("items", items));
    }

    /** 2.7 revokeSession（FLOW-07 FUNC-012）：归属校验下沉 Service（EDGE-009） */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId) {
        sessionService.revoke(principal().subject(), sessionId);
        return ResponseEntity.noContent().build();
    }

    /** 2.8 revokeOtherSessions（FLOW-07 FUNC-012） */
    @DeleteMapping("/sessions/others")
    public ResponseEntity<Void> revokeOtherSessions() {
        AuthPrincipal p = principal();
        sessionService.revokeAllExcept(p.subject(), p.tokenId());
        return ResponseEntity.noContent().build();
    }

    /** 2.9 deleteAccount（FLOW-08 FUNC-027） */
    @PostMapping("/delete")
    public ResponseEntity<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest req) {
        if (!Boolean.TRUE.equals(req.confirm())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        identityService.deleteAccount(principal().subject());
        return ResponseEntity.noContent().build();
    }

    private AuthPrincipal principal() {
        AuthPrincipal p = AuthContext.get();
        if (p == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return p;
    }

    public record BindRequest(String provider, String idToken, String email, String code) {}
    public record ChangePrimaryRequest(@NotBlank String newEmail, @NotBlank String code) {}
    public record DeleteAccountRequest(@NotNull Boolean confirm) {}
}
