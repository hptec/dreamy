package com.dreamy.controller;

import com.dreamy.enums.AuthProvider;
import com.dreamy.domain.user.service.IdentityService;
import com.dreamy.dto.IdentityDTO;
import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.security.AuthContext;
import com.dreamy.security.AuthPrincipal;
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

    public AccountController(IdentityService identityService) {
        this.identityService = identityService;
    }

    /** 2.1 getProfile（FUNC-007） */
    @GetMapping("/profile")
    public ResponseEntity<R<com.dreamy.dto.UserProfileDTO>> getProfile() {
        return ResponseEntity.ok(R.ok(identityService.getProfileView(userId())));
    }

    /**
     * 2.1b updateProfile（FUNC-019 / 决策13）。更新 display_name / locale_pref，仅更新提供字段。
     * StoreBearerAuth（消费端登录态）：StoreJwtFilter 守 /api/store/** 前缀强制鉴权。
     * 注：L2 设计标注路径 /api/consumer/auth/profile，但本工程 StoreBearerAuth 仅在 /api/store/ 前缀生效，
     * 落 /api/consumer/ 将成无鉴权端点；故置于 /api/store/account/profile（同 StoreBearerAuth 口径）。
     */
    @PutMapping("/profile")
    public ResponseEntity<R<com.dreamy.dto.UserProfileDTO>> updateProfile(
            @Valid @RequestBody ProfileUpdateRequest req) {
        return ResponseEntity.ok(R.ok(
                identityService.updateProfileView(userId(), req.displayName(), req.localePref())));
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

    public record BindRequest(AuthProvider provider, String idToken, String email, String code) {}
    public record ChangePrimaryRequest(@NotBlank String newEmail, @NotBlank String code) {}
    public record DeleteAccountRequest(@NotNull Boolean confirm) {}

    /** FUNC-019：display_name ≤ 64；locale_pref ∈ {en,es,fr}（均可选，仅更新提供字段） */
    public record ProfileUpdateRequest(
            @jakarta.validation.constraints.Size(max = 64) String displayName,
            @jakarta.validation.constraints.Pattern(regexp = "en|es|fr") String localePref) {}
}
