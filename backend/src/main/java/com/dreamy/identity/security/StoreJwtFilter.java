package com.dreamy.identity.security;

import com.dreamy.identity.error.BizException;
import com.dreamy.identity.i18n.RequestLocaleContext;
import com.dreamy.identity.infra.SessionValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Store JWT 鉴权过滤器（/api/store/* 前缀）。
 * 约束: BE-DIM-6 按前缀选 STORE_JWT_SECRET 解析；跨端误用 40100（EDGE-024）；
 * Accept-Language 设置 RequestLocaleContext（en/es/fr，缺省 en）；PATH-03 过滤器直接短路 401。
 *
 * 本 change（portal-api-integration）升级为四段裁决（showroom-api-detail 0.2-2，权威设计；
 * 与 review REV-IMPL-FILTER method-aware 白名单配置化同一次升级）：
 * ① 命中 method-aware 公开白名单（dreamy.security.store-public-paths）→ 放行（principal 可选注入，
 *    catalog §0 / showroom 0.1 口径：携带可解析有效 store JWT → 注入；解析失败/无 token → 匿名放行不报错）。
 *    identity 既有 5 条公开路径迁入配置，行为保持；Stripe webhook（POST /api/store/payments/stripe/webhook）
 *    经白名单豁免 JWT，由专用 Stripe 签名过滤器接管验签（trading §0/§4，webhook 安全第 5 条）。
 * ② Bearer 解析（storeKey）成功且 typ=store → 既有链路（SessionValidator + store principal）不变。
 * ③ typ=guest 旁路：a.作用域 /api/store/showrooms/** 之外 → 401 40100（跨用途误用，CP-021 同口径）；
 *    b.guest 操作白名单（dreamy.security.showroom-guest-paths，条目形式与 ① 完全一致）不匹配 → 403 403102；
 *    c.路径 {id} != claims.showroom_id → 403 403102；
 *    d.ShowroomGuestValidator（showroom 域实现注入，缺席 fail-closed）行不存在或 inv_ver 不等 → 401 401101；
 *    e.注入 guest 受限主体 AuthPrincipal(subject=member_id, typ=guest) + GuestContext{showroomId, memberId}。
 * ④ 解析异常：过期且未验签 claims typ=guest → 401 401101（guest 过期专属码）；其余 → 401 40100。
 * 日志脱敏（0.2-4）：guest JWT 原文一律 [REDACTED]，日志仅记 showroom_id + member_id。
 */
@Component
public class StoreJwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(StoreJwtFilter.class);

    private static final String STORE_PREFIX = "/api/store/";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER = "Bearer ";
    /** guest 旁路作用域（showroom-api-detail 0.2-③a） */
    private static final String GUEST_SCOPE_ROOT = "/api/store/showrooms";

    /** identity 既有 401 口径 */
    private static final int CODE_UNAUTHORIZED = 40100;
    /** showroom 域段 1：guest JWT 无效/过期/随邀请重置失效（error-strategy showroom 码表） */
    private static final int CODE_GUEST_TOKEN_INVALID = 401101;
    /** showroom 域段 1：guest 越权访问非绑定协作空间/未放行操作 */
    private static final int CODE_GUEST_SCOPE_EXCEEDED = 403102;

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionValidator sessionValidator;
    private final MethodAwarePathMatcher publicPathMatcher;
    private final MethodAwarePathMatcher guestPathMatcher;
    private final ObjectProvider<ShowroomGuestValidator> guestValidatorProvider;

    public StoreJwtFilter(JwtTokenProvider jwtTokenProvider,
                          SessionValidator sessionValidator,
                          StoreSecurityProperties securityProperties,
                          ObjectProvider<ShowroomGuestValidator> guestValidatorProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionValidator = sessionValidator;
        this.publicPathMatcher = new MethodAwarePathMatcher(securityProperties.getStorePublicPaths());
        this.guestPathMatcher = new MethodAwarePathMatcher(securityProperties.getShowroomGuestPaths());
        this.guestValidatorProvider = guestValidatorProvider;
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
        RequestLocaleContext.set(resolveLocale(request.getHeader("Accept-Language")));
        try {
            decide(request, response, chain);
        } catch (BizException ex) {
            // 既有口径：链路内未被 GlobalExceptionHandler 兜住的 BizException → 401
            if (!response.isCommitted()) {
                writeError(response, 401, CODE_UNAUTHORIZED, "Authentication required");
            }
        } finally {
            RequestLocaleContext.clear();
            AuthContext.clear();
            GuestContext.clear();
        }
    }

    private void decide(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String token = extractBearer(request);

        // ① method-aware 公开白名单 → 放行（principal 可选注入）
        if (publicPathMatcher.matches(method, path)) {
            if (token != null) {
                tryInjectStorePrincipal(token);
            }
            chain.doFilter(request, response);
            return;
        }

        if (token == null) {
            writeError(response, 401, CODE_UNAUTHORIZED, "Authentication required");
            return;
        }

        JwtTokenProvider.StoreBearer bearer;
        try {
            bearer = jwtTokenProvider.parseStoreBearer(token);
        } catch (GuestTokenInvalidException ex) {
            // ④ guest 过期/非法分型专属码（token 原文不入日志）
            writeError(response, 401, CODE_GUEST_TOKEN_INVALID,
                    "Guest access credential is invalid or expired, please reopen the invitation link");
            return;
        } catch (BizException ex) {
            // ④ 签名非法/typ 缺失/store 过期/跨端误用 → 既有口径
            writeError(response, 401, CODE_UNAUTHORIZED, "Authentication required");
            return;
        }

        if (!bearer.isGuest()) {
            // ② typ=store 既有链路（SessionValidator + store principal）不变
            AuthPrincipal principal = bearer.principal();
            // BLOCKER-1：会话有效性校验（EDGE-023 即时失效）。
            // 撤销/登出/强制下线/禁用后 Redis 单级键被 DEL，DB session.status=revoked → 此处 401，
            // 不再等待 token 自然过期（store access 2h）。Redis 命中即放行（QP-003），未命中降级查 DB（DG-003）。
            if (!sessionValidator.isStoreSessionValid(principal.tokenId())) {
                writeError(response, 401, CODE_UNAUTHORIZED, "Authentication required");
                return;
            }
            AuthContext.set(principal);
            chain.doFilter(request, response);
            return;
        }

        // ③ guest 旁路四段裁决
        JwtTokenProvider.GuestClaims guest = bearer.guest();
        // a. 旁路作用域 /api/store/showrooms/**
        if (!path.equals(GUEST_SCOPE_ROOT) && !path.startsWith(GUEST_SCOPE_ROOT + "/")) {
            writeError(response, 401, CODE_UNAUTHORIZED, "Authentication required");
            return;
        }
        // b. guest 操作白名单（method-aware，与公开白名单同一匹配器实现）
        if (!guestPathMatcher.matches(method, path)) {
            writeError(response, 403, CODE_GUEST_SCOPE_EXCEEDED, "No access to this showroom");
            return;
        }
        // c. 路径 {id} 与 claims.showroom_id 等值
        String pathId = extractShowroomPathId(path);
        if (!String.valueOf(guest.showroomId()).equals(pathId)) {
            log.warn("[STORE-JWT] guest scope exceeded showroom_id={} member_id={} (token [REDACTED])",
                    guest.showroomId(), guest.memberId());
            writeError(response, 403, CODE_GUEST_SCOPE_EXCEEDED, "No access to this showroom");
            return;
        }
        // d. ShowroomGuestValidator：行不存在（已删除）/invite_version 不等（邀请已重置）→ 401101；
        //    showroom 域实现缺席 → fail-closed 拒绝
        ShowroomGuestValidator validator = guestValidatorProvider.getIfAvailable();
        if (validator == null || !validator.isGuestSessionValid(guest.showroomId(), guest.inviteVersion())) {
            writeError(response, 401, CODE_GUEST_TOKEN_INVALID,
                    "Guest access credential is invalid or expired, please reopen the invitation link");
            return;
        }
        // e. 注入 guest 受限主体 + 旁路上下文（服务层据此取 my_member 身份，不信任请求体身份字段）
        AuthContext.set(new AuthPrincipal(guest.subject(), guest.tokenId(), AuthPrincipal.TYPE_GUEST,
                null, false, null, null));
        GuestContext.set(new GuestContext(guest.showroomId(), guest.memberId()));
        chain.doFilter(request, response);
    }

    /** 白名单放行的 principal 可选注入：有效 store JWT 且会话有效 → 注入；其余一律匿名放行不报错 */
    private void tryInjectStorePrincipal(String token) {
        try {
            JwtTokenProvider.StoreBearer bearer = jwtTokenProvider.parseStoreBearer(token);
            if (!bearer.isGuest() && sessionValidator.isStoreSessionValid(bearer.principal().tokenId())) {
                AuthContext.set(bearer.principal());
            }
        } catch (RuntimeException ignored) {
            // 解析失败/guest 过期等 → 匿名放行（showroom 0.1 口径）
        }
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER)) {
            return null;
        }
        String token = header.substring(BEARER.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** 取 /api/store/showrooms/{id}/... 的 {id} 段；路径不含 id 段返回空串（必然与 claims 不等） */
    private String extractShowroomPathId(String path) {
        String prefix = GUEST_SCOPE_ROOT + "/";
        if (!path.startsWith(prefix)) {
            return "";
        }
        String rest = path.substring(prefix.length());
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
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

    private void writeError(HttpServletResponse response, int httpStatus, int code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\"}");
    }
}
