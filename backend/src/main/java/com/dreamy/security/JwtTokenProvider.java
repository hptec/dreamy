package com.dreamy.security;

import com.dreamy.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 双密钥 JWT 工具：签发/解析 store 与 admin token。
 * 约束: shared-contracts jwt_isolation；DR-01 独立密钥不复用；EDGE-024 跨端误用由过滤器据 type 拒 40100。
 * 签发 store TokenPair(access 2h + refresh 30d)；admin access 8h 无 refresh。
 * showroom guest JWT（showroom-api-detail 0.2-1）：以 storeKey 签名（同基建复用，不新增密钥；guest 属
 * 消费端体系，按 typ claim 区分，与 CP-020 双密钥隔离不冲突）；claims sub=member_id/jti/typ=guest/
 * showroom_id/member_id/inv_ver；TTL 配置项 dreamy.showroom.guest-token-ttl-seconds 缺省 86400（24h）；
 * 无 refresh、不落 user_session（失效由 inv_ver 等值校验与资源存在性承担，不走 SessionValidator）。
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_METHOD = "method";
    private static final String CLAIM_REFRESH = "refresh";
    private static final String CLAIM_ROLE_ID = "role_id";
    private static final String CLAIM_SHOWROOM_ID = "showroom_id";
    private static final String CLAIM_MEMBER_ID = "member_id";
    private static final String CLAIM_INV_VER = "inv_ver";

    private final JwtProperties props;
    private final SecretKey storeKey;
    private final SecretKey adminKey;
    /** guest JWT TTL（showroom-api-detail 0.2-1，缺省 24h） */
    private final long guestTokenTtlSeconds;

    public JwtTokenProvider(JwtProperties props) {
        this(props, 86400L);
    }

    @Autowired
    public JwtTokenProvider(JwtProperties props,
                            @Value("${dreamy.showroom.guest-token-ttl-seconds:86400}") long guestTokenTtlSeconds) {
        this.props = props;
        byte[] storeSecret = requireKey(props.getStore().getSecret(), "STORE_JWT_SECRET");
        byte[] adminSecret = requireKey(props.getAdmin().getSecret(), "ADMIN_JWT_SECRET");
        if (MessageDigest.isEqual(storeSecret, adminSecret)) {
            throw new IllegalStateException("STORE_JWT_SECRET and ADMIN_JWT_SECRET must be different");
        }
        this.storeKey = Keys.hmacShaKeyFor(storeSecret);
        this.adminKey = Keys.hmacShaKeyFor(adminSecret);
        this.guestTokenTtlSeconds = guestTokenTtlSeconds;
    }

    /** HMAC-SHA256 要求至少 256 bit；禁止将空值或短值静默补齐成可预测密钥。 */
    private byte[] requireKey(String secret, String variableName) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(variableName + " must be configured");
        }
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException(variableName + " must contain at least 32 bytes");
        }
        return raw;
    }

    // ===== store 签发（access 2h + refresh 30d） =====
    public TokenPair issueStoreTokens(String userId, String method) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime accessExp = now.plusSeconds(props.getStore().getAccessTtlSeconds());
        LocalDateTime refreshExp = now.plusSeconds(props.getStore().getRefreshTtlSeconds());
        String jti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        String access = Jwts.builder()
                .issuer(props.getStore().getIssuer())
                .subject(userId)
                .id(jti)
                .claim(CLAIM_TYPE, AuthPrincipal.TYPE_STORE)
                .claim(CLAIM_METHOD, method)
                .claim(CLAIM_REFRESH, false)
                .issuedAt(Date.from(now.toInstant(ZoneOffset.UTC)))
                .expiration(Date.from(accessExp.toInstant(ZoneOffset.UTC)))
                .signWith(storeKey)
                .compact();

        String refresh = Jwts.builder()
                .issuer(props.getStore().getIssuer())
                .subject(userId)
                .id(refreshJti)
                .claim(CLAIM_TYPE, AuthPrincipal.TYPE_STORE)
                .claim(CLAIM_METHOD, method)
                .claim(CLAIM_REFRESH, true)
                .issuedAt(Date.from(now.toInstant(ZoneOffset.UTC)))
                .expiration(Date.from(refreshExp.toInstant(ZoneOffset.UTC)))
                .signWith(storeKey)
                .compact();

        TokenPair pair = new TokenPair();
        pair.setAccessToken(access);
        pair.setRefreshToken(refresh);
        pair.setAccessExpiresAt(accessExp);
        pair.setRefreshExpiresAt(refreshExp);
        pair.setTokenId(jti);
        pair.setRefreshTokenId(refreshJti);
        return pair;
    }

    /** FLOW-04 滑动续期：基于既有 user/method 重新签发 access+refresh */
    public TokenPair reissueStoreTokens(String userId, String method) {
        return issueStoreTokens(userId, method);
    }

    // ===== admin 签发（access 8h 无 refresh） =====
    public AdminToken issueAdminToken(String adminId, String roleId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime exp = now.plusSeconds(props.getAdmin().getAccessTtlSeconds());
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .issuer(props.getAdmin().getIssuer())
                .subject(adminId)
                .id(jti)
                .claim(CLAIM_TYPE, AuthPrincipal.TYPE_ADMIN)
                .claim(CLAIM_ROLE_ID, roleId)
                .claim(CLAIM_REFRESH, false)
                .issuedAt(Date.from(now.toInstant(ZoneOffset.UTC)))
                .expiration(Date.from(exp.toInstant(ZoneOffset.UTC)))
                .signWith(adminKey)
                .compact();
        return new AdminToken(token, jti, exp);
    }

    /** 解析 store token；签名/类型/过期校验失败抛 UNAUTHORIZED(40100)（EX-03） */
    public AuthPrincipal parseStoreToken(String token) {
        Claims c = parse(token, storeKey);
        requireType(c, AuthPrincipal.TYPE_STORE);
        Boolean refresh = c.get(CLAIM_REFRESH, Boolean.class);
        return new AuthPrincipal(c.getSubject(), c.getId(), AuthPrincipal.TYPE_STORE,
                c.get(CLAIM_METHOD, String.class), Boolean.TRUE.equals(refresh), null, null);
    }

    // ===== showroom guest 签发/解析（showroom-api-detail 0.2-1，storeKey 复用） =====

    /** 签发 guest JWT：claims sub=member_id / jti / typ=guest / showroom_id / member_id / inv_ver */
    public GuestToken issueShowroomGuestToken(long memberId, long showroomId, long inviteVersion) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime exp = now.plusSeconds(guestTokenTtlSeconds);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .issuer(props.getStore().getIssuer())
                .subject(String.valueOf(memberId))
                .id(jti)
                .claim(CLAIM_TYPE, AuthPrincipal.TYPE_GUEST)
                .claim(CLAIM_SHOWROOM_ID, showroomId)
                .claim(CLAIM_MEMBER_ID, memberId)
                .claim(CLAIM_INV_VER, inviteVersion)
                .issuedAt(Date.from(now.toInstant(ZoneOffset.UTC)))
                .expiration(Date.from(exp.toInstant(ZoneOffset.UTC)))
                .signWith(storeKey)
                .compact();
        return new GuestToken(token, jti, exp);
    }

    /**
     * 解析 store 侧 Bearer 并按 typ 分型（StoreJwtFilter 四段裁决 ②/③/④ 的解析入口）：
     * - typ=store → StoreBearer.principal（既有链路语义不变）
     * - typ=guest → StoreBearer.guest（claims showroom_id/member_id/inv_ver）
     * - 过期且未验签 claims typ=guest → GuestTokenInvalidException（401101 专属分型）
     * - 其余（签名非法/typ 缺失或未知/store 过期）→ BizException UNAUTHORIZED（40100 既有口径）
     */
    public StoreBearer parseStoreBearer(String token) {
        Claims c;
        try {
            c = Jwts.parser().verifyWith(storeKey).build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException ex) {
            // 0.2-④：ExpiredJwtException（可读未验签 claims）且 claims.typ=guest → 401101
            Object typ = ex.getClaims() == null ? null : ex.getClaims().get(CLAIM_TYPE);
            if (AuthPrincipal.TYPE_GUEST.equals(typ)) {
                throw new GuestTokenInvalidException("guest token expired");
            }
            throw new com.dreamy.error.BizException(ErrorCode.UNAUTHORIZED);
        } catch (Exception ex) {
            throw new com.dreamy.error.BizException(ErrorCode.UNAUTHORIZED);
        }
        String typ = c.get(CLAIM_TYPE, String.class);
        if (AuthPrincipal.TYPE_STORE.equals(typ)) {
            Boolean refresh = c.get(CLAIM_REFRESH, Boolean.class);
            return new StoreBearer(new AuthPrincipal(c.getSubject(), c.getId(), AuthPrincipal.TYPE_STORE,
                    c.get(CLAIM_METHOD, String.class), Boolean.TRUE.equals(refresh), null, null), null);
        }
        if (AuthPrincipal.TYPE_GUEST.equals(typ)) {
            Long showroomId = asLong(c.get(CLAIM_SHOWROOM_ID));
            Long memberId = asLong(c.get(CLAIM_MEMBER_ID));
            Long invVer = asLong(c.get(CLAIM_INV_VER));
            if (showroomId == null || memberId == null || invVer == null) {
                // 非法分型：guest 必备 claims 缺失
                throw new GuestTokenInvalidException("guest claims incomplete");
            }
            return new StoreBearer(null, new GuestClaims(showroomId, memberId, invVer, c.getId(), c.getSubject()));
        }
        // typ 缺失/未知（含跨端误用语义，CP-021）
        throw new com.dreamy.error.BizException(ErrorCode.UNAUTHORIZED);
    }

    private Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 解析 admin token；签名/类型/过期校验失败抛 UNAUTHORIZED(40100) */
    public AuthPrincipal parseAdminToken(String token) {
        Claims c = parse(token, adminKey);
        requireType(c, AuthPrincipal.TYPE_ADMIN);
        return new AuthPrincipal(c.getSubject(), c.getId(), AuthPrincipal.TYPE_ADMIN,
                null, false, c.get(CLAIM_ROLE_ID, String.class), null);
    }

    private Claims parse(String token, SecretKey key) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception ex) {
            throw new com.dreamy.error.BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void requireType(Claims c, String expected) {
        if (!expected.equals(c.get(CLAIM_TYPE, String.class))) {
            // 跨端 token 误用（EDGE-024）
            throw new com.dreamy.error.BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** admin 签发结果（无 refresh） */
    public record AdminToken(String token, String tokenId, LocalDateTime expiresAt) {
    }

    /** guest 签发结果（无 refresh、不落 user_session） */
    public record GuestToken(String token, String tokenId, LocalDateTime expiresAt) {
    }

    /** guest JWT claims 投影（showroom-api-detail 0.2-1） */
    public record GuestClaims(long showroomId, long memberId, long inviteVersion, String tokenId, String subject) {
    }

    /** store 侧 Bearer 解析结果（store 与 guest 二选一） */
    public record StoreBearer(AuthPrincipal principal, GuestClaims guest) {
        public boolean isGuest() {
            return guest != null;
        }
    }
}
