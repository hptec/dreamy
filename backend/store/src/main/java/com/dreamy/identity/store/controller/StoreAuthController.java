package com.dreamy.identity.store.controller;

import com.dreamy.identity.common.domain.model.LoginContext;
import com.dreamy.identity.common.domain.model.LoginResult;
import com.dreamy.identity.common.domain.service.AuthConfigService;
import com.dreamy.identity.common.domain.service.OtpService;
import com.dreamy.identity.common.domain.service.IdentityService;
import com.dreamy.identity.common.domain.service.SessionService;
import com.dreamy.identity.common.dto.AuthConfigView;
import com.dreamy.identity.common.dto.mapper.IdentityDtoMapper;
import com.dreamy.identity.common.error.BizException;
import com.dreamy.identity.common.error.ErrorCode;
import com.dreamy.identity.common.security.AuthContext;
import com.dreamy.identity.common.security.AuthPrincipal;
import com.dreamy.identity.common.security.TokenPair;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Store 认证控制器（/api/store/auth/*）。
 * 约束: FLOW-01~04；V-001~007；FUNC-001~005/030；EDGE-001~006/022/024/025。
 */
@RestController
@RequestMapping("/api/store/auth")
public class StoreAuthController {

    private final OtpService otpService;
    private final IdentityService identityService;
    private final SessionService sessionService;
    private final AuthConfigService authConfigService;
    private final IdentityDtoMapper mapper;
    private final com.dreamy.identity.common.security.JwtTokenProvider jwtTokenProvider;

    public StoreAuthController(OtpService otpService, IdentityService identityService,
                               SessionService sessionService, AuthConfigService authConfigService,
                               IdentityDtoMapper mapper,
                               com.dreamy.identity.common.security.JwtTokenProvider jwtTokenProvider) {
        this.otpService = otpService;
        this.identityService = identityService;
        this.sessionService = sessionService;
        this.authConfigService = authConfigService;
        this.mapper = mapper;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /** 1.1 sendOtp — POST /api/store/auth/otp/send（FLOW-01 FUNC-001） */
    @PostMapping("/otp/send")
    public ResponseEntity<Map<String, Object>> sendOtp(@Valid @RequestBody SendOtpRequest req,
                                                       HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        String locale = req.locale() != null ? req.locale() : "en";
        OtpService.SendOtpResult result = otpService.sendOtp(req.email(), locale, ip);
        return ResponseEntity.ok(Map.of(
                "resend_after_seconds", result.resendAfterSeconds(),
                "otp_length", result.otpLength()));
    }

    /** 1.2 verifyOtp — POST /api/store/auth/otp/verify（FLOW-02 FUNC-002） */
    @PostMapping("/otp/verify")
    public ResponseEntity<Map<String, Object>> verifyOtp(@Valid @RequestBody VerifyOtpRequest req,
                                                         HttpServletRequest http) {
        LoginContext ctx = extractContext(http);
        LoginResult result = otpService.verifyOtp(req.email(), req.code(), ctx);
        return ResponseEntity.ok(buildLoginResponse(result));
    }

    /** 1.3 oidcCallback — POST /api/store/auth/oidc/{provider}/callback（FLOW-03 FUNC-004/005） */
    @PostMapping("/oidc/{provider}/callback")
    public ResponseEntity<Map<String, Object>> oidcCallback(
            @PathVariable @Pattern(regexp = "google|apple") String provider,
            @Valid @RequestBody OidcCallbackRequest req,
            HttpServletRequest http) {
        LoginContext ctx = extractContext(http);
        LoginResult result = identityService.oidcLogin(provider, req.idToken(), req.nonce(), ctx);
        return ResponseEntity.ok(buildLoginResponse(result));
    }

    /** 1.4 refreshToken — POST /api/store/auth/refresh（FLOW-04 FUNC-030） */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@Valid @RequestBody RefreshRequest req) {
        com.dreamy.identity.common.security.AuthPrincipal p;
        try {
            p = jwtTokenProvider.parseStoreToken(req.refreshToken());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.REFRESH_INVALID);
        }
        TokenPair pair = sessionService.refresh(p.tokenId());
        return ResponseEntity.ok(Map.of("tokens", tokenMap(pair)));
    }

    /** 1.5 getStoreAuthConfig — GET /api/store/auth/config（FUNC-003） */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getAuthConfig() {
        AuthConfigView cfg = authConfigService.getConfigView();
        return ResponseEntity.ok(Map.of(
                "email_enabled", cfg.emailEnabled(),
                "google_enabled", cfg.googleEnabled(),
                "apple_enabled", cfg.appleEnabled(),
                "otp_length", cfg.otpLength()));
    }

    // ===== helpers =====

    private Map<String, Object> buildLoginResponse(LoginResult r) {
        return Map.of(
                "tokens", tokenMap(r.tokens()),
                "user", mapper.toProfile(r.user()),
                "is_new_account", r.newAccount());
    }

    private Map<String, Object> tokenMap(TokenPair p) {
        return Map.of(
                "access_token", p.getAccessToken(),
                "refresh_token", p.getRefreshToken(),
                "access_expires_at", p.getAccessExpiresAt(),
                "refresh_expires_at", p.getRefreshExpiresAt());
    }

    private LoginContext extractContext(HttpServletRequest http) {
        String ua = http.getHeader("User-Agent");
        return new LoginContext(http.getRemoteAddr(), ua, null, null);
    }

    // ===== request records =====

    public record SendOtpRequest(
            @NotBlank @Email String email,
            @Pattern(regexp = "en|es|fr") String locale) {
    }

    public record VerifyOtpRequest(
            @NotBlank @Email String email,
            @NotBlank String code) {
    }

    public record OidcCallbackRequest(
            @NotBlank String idToken,
            String nonce) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }
}
