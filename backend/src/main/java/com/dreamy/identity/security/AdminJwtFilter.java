package com.dreamy.identity.security;

import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.i18n.RequestLocaleContext;
import com.dreamy.identity.infra.SessionValidator;
import com.dreamy.identity.security.AuthContext;
import com.dreamy.identity.security.AuthPrincipal;
import com.dreamy.identity.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Admin JWT 鉴权过滤器（/api/admin/* 前缀）。
 * 约束: BE-DIM-6 按前缀选 ADMIN_JWT_SECRET；跨端误用 40100（EDGE-024）；admin 固定 zh；PATH-03。
 */
@Component
public class AdminJwtFilter extends OncePerRequestFilter {

    private static final String ADMIN_PREFIX = "/api/admin/";
    private static final String BEARER = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionValidator sessionValidator;

    public AdminJwtFilter(JwtTokenProvider jwtTokenProvider, SessionValidator sessionValidator) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionValidator = sessionValidator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        // admin 固定中文
        RequestLocaleContext.set(Locale.CHINESE);

        if (isPublic(request.getRequestURI())) {
            try {
                chain.doFilter(request, response);
            } finally {
                RequestLocaleContext.clear();
                AuthContext.clear();
            }
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            writeUnauthorized(response);
            RequestLocaleContext.clear();
            return;
        }
        try {
            AuthPrincipal principal = jwtTokenProvider.parseAdminToken(header.substring(BEARER.length()));
            if (!AuthPrincipal.TYPE_ADMIN.equals(principal.type())) {
                writeUnauthorized(response);
                RequestLocaleContext.clear();
                return;
            }
            // BLOCKER-1：admin 会话有效性校验（admin_session.status=active）。
            // adminLogout/禁用管理员级联撤销后即时失效，不等待 admin token 8h 自然过期。
            // Redis 命中即放行，未命中降级查 DB（DG-003）。
            if (!sessionValidator.isAdminSessionValid(principal.tokenId())) {
                writeUnauthorized(response);
                RequestLocaleContext.clear();
                return;
            }
            AuthContext.set(principal);
            chain.doFilter(request, response);
        } catch (BizException ex) {
            writeUnauthorized(response);
        } finally {
            RequestLocaleContext.clear();
            AuthContext.clear();
        }
    }

    private boolean isPublic(String path) {
        return path.equals("/api/admin/auth/login");
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":40100,\"message\":\"未认证\"}");
    }
}
