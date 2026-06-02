package com.dreamy.identity.common.security;

import com.dreamy.identity.common.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 双密钥 JWT 工具：签发/解析 store 与 admin token。
 * 约束: shared-contracts jwt_isolation；DR-01 独立密钥不复用；EDGE-024 跨端误用由过滤器据 type 拒 40100。
 * 签发 store TokenPair(access 2h + refresh 30d)；admin access 8h 无 refresh。
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_METHOD = "method";
    private static final String CLAIM_REFRESH = "refresh";
    private static final String CLAIM_ROLE_ID = "role_id";
    private static final String CLAIM_PERMS = "permission_keys";

    private final JwtProperties props;
    private final SecretKey storeKey;
    private final SecretKey adminKey;

    public JwtTokenProvider(JwtProperties props) {
        this.props = props;
        this.storeKey = Keys.hmacShaKeyFor(padKey(props.getStore().getSecret()));
        this.adminKey = Keys.hmacShaKeyFor(padKey(props.getAdmin().getSecret()));
    }

    /** HMAC-SHA256 要求 >=256bit 密钥；不足右补，保证沙箱默认密钥也可用 */
    private byte[] padKey(String secret) {
        byte[] raw = (secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8);
        if (raw.length >= 32) {
            return raw;
        }
        byte[] padded = new byte[32];
        System.arraycopy(raw, 0, padded, 0, raw.length);
        for (int i = raw.length; i < 32; i++) {
            padded[i] = (byte) ('0' + (i % 10));
        }
        return padded;
    }

    // ===== store 签发（access 2h + refresh 30d） =====
    public TokenPair issueStoreTokens(String userId, String method) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime accessExp = now.plusSeconds(props.getStore().getAccessTtlSeconds());
        OffsetDateTime refreshExp = now.plusSeconds(props.getStore().getRefreshTtlSeconds());
        String jti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        String access = Jwts.builder()
                .issuer(props.getStore().getIssuer())
                .subject(userId)
                .id(jti)
                .claim(CLAIM_TYPE, AuthPrincipal.TYPE_STORE)
                .claim(CLAIM_METHOD, method)
                .claim(CLAIM_REFRESH, false)
                .issuedAt(Date.from(now.toInstant()))
                .expiration(Date.from(accessExp.toInstant()))
                .signWith(storeKey)
                .compact();

        String refresh = Jwts.builder()
                .issuer(props.getStore().getIssuer())
                .subject(userId)
                .id(refreshJti)
                .claim(CLAIM_TYPE, AuthPrincipal.TYPE_STORE)
                .claim(CLAIM_METHOD, method)
                .claim(CLAIM_REFRESH, true)
                .issuedAt(Date.from(now.toInstant()))
                .expiration(Date.from(refreshExp.toInstant()))
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
    public AdminToken issueAdminToken(String adminId, String roleId, List<String> permissionKeys) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime exp = now.plusSeconds(props.getAdmin().getAccessTtlSeconds());
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .issuer(props.getAdmin().getIssuer())
                .subject(adminId)
                .id(jti)
                .claim(CLAIM_TYPE, AuthPrincipal.TYPE_ADMIN)
                .claim(CLAIM_ROLE_ID, roleId)
                .claim(CLAIM_PERMS, permissionKeys)
                .claim(CLAIM_REFRESH, false)
                .issuedAt(Date.from(now.toInstant()))
                .expiration(Date.from(exp.toInstant()))
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

    /** 解析 admin token；签名/类型/过期校验失败抛 UNAUTHORIZED(40100) */
    @SuppressWarnings("unchecked")
    public AuthPrincipal parseAdminToken(String token) {
        Claims c = parse(token, adminKey);
        requireType(c, AuthPrincipal.TYPE_ADMIN);
        List<String> perms = c.get(CLAIM_PERMS, List.class);
        return new AuthPrincipal(c.getSubject(), c.getId(), AuthPrincipal.TYPE_ADMIN,
                null, false, c.get(CLAIM_ROLE_ID, String.class), perms);
    }

    private Claims parse(String token, SecretKey key) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception ex) {
            throw new com.dreamy.identity.common.error.BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void requireType(Claims c, String expected) {
        if (!expected.equals(c.get(CLAIM_TYPE, String.class))) {
            // 跨端 token 误用（EDGE-024）
            throw new com.dreamy.identity.common.error.BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** admin 签发结果（无 refresh） */
    public record AdminToken(String token, String tokenId, OffsetDateTime expiresAt) {
    }
}
