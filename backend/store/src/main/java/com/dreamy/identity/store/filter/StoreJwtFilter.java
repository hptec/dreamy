package com.dreamy.identity.store.filter;

import com.dreamy.identity.common.error.BizException;
import com.dreamy.identity.common.error.ErrorCode;
import com.dreamy.identity.common.i18n.RequestLocaleContext;
import com.dreamy.identity.common.infra.SessionValidator;
import com.dreamy.identity.common.security.AuthContext;
import com.dreamy.identity.common.security.AuthPrincipal;
import com.dreamy.identity.common.security.JwtTokenProvider;
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
 * Store JWT 鉴权过滤器（/api/store/* 前缀）。
 * 约束: BE-DIM-6 按前缀选 STORE_JWT_SECRET 解析；跨端误用 40100（EDGE-024）；
 * Accept-Language 设置 RequestLocaleContext（en/es/fr，缺省 en）；PATH-03 过滤器直接短路 401。
 */
@Component
public class StoreJwtFilter extends OncePerRequestFilter {

    private static final String STORE_PREFIX = "/api/store/";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionValidator sessionValidator;

    public StoreJwtFilter(JwtTokenProvider jwtTokenProvider, SessionValidator sessionValidator) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionValidator = sessionValidator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(STORE_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        // i18n：Accept-Language → RequestLocaleContext
        String lang = request.getHeader("Accept-Language");
        RequestLocaleContext.set(resolveLocale(lang));

        String path = request.getRequestURI();
        // 公开端点无需 token
        if (isPublic(path)) {
            try {
                chain.doFilter(request, response);
            } finally {
                RequestLocaleContext.clear();
                AuthContext.clear();
            }
            return;
        }

        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER)) {
            writeUnauthorized(response);
            RequestLocaleContext.clear();
            return;
        }
        String token = header.substring(BEARER.length());
        try {
            AuthPrincipal principal = jwtTokenProvider.parseStoreToken(token);
            // 跨端误用：store token 命中 /api/admin/* 由 AdminJwtFilter 处理；此处确保 typ=store
            if (!AuthPrincipal.TYPE_STORE.equals(principal.type())) {
                writeUnauthorized(response);
                RequestLocaleContext.clear();
                return;
            }
            // BLOCKER-1：会话有效性校验（EDGE-023 即时失效）。
            // 撤销/登出/强制下线/禁用后 Redis 单级键被 DEL，DB session.status=revoked → 此处 401，
            // 不再等待 token 自然过期（store access 2h）。Redis 命中即放行（QP-003），未命中降级查 DB（DG-003）。
            if (!sessionValidator.isStoreSessionValid(principal.tokenId())) {
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
        return path.equals("/api/store/auth/otp/send")
                || path.equals("/api/store/auth/otp/verify")
                || path.startsWith("/api/store/auth/oidc/")
                || path.equals("/api/store/auth/refresh")
                || path.equals("/api/store/auth/config");
    }

    private Locale resolveLocale(String lang) {
        if (lang == null) {
            return Locale.ENGLISH;
        }
        String l = lang.toLowerCase();
        if (l.startsWith("es")) {
            return Locale.forLanguageTag("es");
        }
        if (l.startsWith("fr")) {
            return Locale.FRENCH;
        }
        return Locale.ENGLISH;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":40100,\"message\":\"Authentication required\"}");
    }
}
